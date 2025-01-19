#include <CoreGraphics/CoreGraphics.h>
#include <IOSurface/IOSurface.h>
#import <Metal/Metal.h>
#define GL_SILENCE_DEPRECATION
#include <pybind11/pybind11.h>
namespace py = pybind11;
#include "dlpack.h"
#include "ipc_apple.h"
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl.h>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

// Sad news: PyTorch does not support from_dlpack for Metal tensors.
// Therefore, we should create a OpenCL dlpack tensor from the IOSurface.
// However OpenCL is not supported in PyTorch!
// Use custom dlpack tensor for now.

IOSurfaceRef getIOSurfaceFromMachPort(mach_port_t machPort) {
    mach_port_type_t portType;
    kern_return_t result =
        mach_port_type(mach_task_self(), machPort, &portType);
    if (result != KERN_SUCCESS) {
        std::string error_msg = "Failed to query Mach Port type: ";
        error_msg += mach_error_string(result);
        throw std::runtime_error(error_msg);
    }
    if (!(portType & MACH_PORT_TYPE_SEND)) {
        std::string error_msg = "Mach Port does not have SEND rights. Type: 0x";
        error_msg += std::to_string(portType);
        throw std::runtime_error(error_msg);
    }

    IOSurfaceRef ioSurface = IOSurfaceLookupFromMachPort(machPort);
    CFRetain(ioSurface);
    return ioSurface;
}

/*
Unused
id<MTLTexture> createMetalTextureFromIOSurface(
    id<MTLDevice> device, IOSurfaceRef ioSurface, int width, int height
) {
    MTLTextureDescriptor *descriptor = [[MTLTextureDescriptor alloc] init];
    descriptor.pixelFormat = MTLPixelFormatRGBA8Unorm;
    descriptor.width = width;
    descriptor.height = height;
    descriptor.usage = MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite;

    id<MTLTexture> texture = [device newTextureWithDescriptor:descriptor
                                                    iosurface:ioSurface
                                                        plane:0];
    return texture;
}
*/

static void deleteDLManagedTensor(DLManagedTensor *self) {
    // CFRelease((__bridge CFTypeRef)self->dl_tensor.data);
    free(self->dl_tensor.shape);
    free(self);
}

static DLManagedTensor *
createDLPackTensorMetal(id<MTLBuffer> mtlBuffer, size_t width, size_t height) {
    DLManagedTensor *tensor =
        (DLManagedTensor *)malloc(sizeof(DLManagedTensor));

    tensor->dl_tensor.data = (void *)CFBridgingRetain(mtlBuffer);
    tensor->dl_tensor.ndim = 3; // H x W x C
    tensor->dl_tensor.shape = (int64_t *)malloc(3 * sizeof(int64_t));
    tensor->dl_tensor.shape[0] = height;
    tensor->dl_tensor.shape[1] = width;
    tensor->dl_tensor.shape[2] = 4; // RGBA
    tensor->dl_tensor.strides = NULL;
    tensor->dl_tensor.byte_offset = 0;

    tensor->dl_tensor.dtype =
        (DLDataType){kDLUInt, 8, 1}; // Unsigned 8-bit integer
    tensor->dl_tensor.device = (DLDevice){kDLMetal, 0}; // metal gpu

    // IOSurfaceIncrementUseCount(ioSurface);
    // avoid crash
    // CFRetain(ioSurface);
    // Set memory deleter
    tensor->deleter = [](DLManagedTensor *self) {
        // IOSurfaceDecrementUseCount(ioSurface);
        deleteDLManagedTensor(self);
    };
    return tensor;
}

PyObject *torchTensorFromDLPack(DLManagedTensor *dlMTensor);

DLManagedTensor *
mtl_dlpack_from_mach_port(unsigned int machPort, int width, int height) {
    IOSurfaceRef ioSurface = getIOSurfaceFromMachPort((mach_port_t)machPort);
    if (!ioSurface) {
        throw std::runtime_error("Failed to initialize IOSurface");
    }

    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    if (!device) {
        throw std::runtime_error("Failed to create Metal device");
    }

    // Calculate the length of the buffer
    size_t iosurface_width = IOSurfaceGetWidth(ioSurface);
    size_t iosurface_height = IOSurfaceGetHeight(ioSurface);
    assert(iosurface_width == width);
    assert(iosurface_height == height);
    size_t bytesPerRow = IOSurfaceGetBytesPerRow(ioSurface);
    size_t length = bytesPerRow * height;

    // Create a Metal buffer from the IOSurface
    id<MTLBuffer> mtlBuffer = [device
        newBufferWithBytesNoCopy:IOSurfaceGetBaseAddress(ioSurface)
                          length:length
                         options:
                             MTLResourceStorageModeShared // MTLResourceStorageModePrivate
                     deallocator:^(void *_Nonnull pointer, NSUInteger length) {
                       // deallocator can be used to free the memory
                       NSLog(
                           @"Deallocator called for buffer with pointer: %p, "
                           @"length: %lu",
                           pointer,
                           (unsigned long)length
                       );
                     }];

    if (!mtlBuffer) {
        throw std::runtime_error("Failed to create Metal buffer from IOSurface"
        );
    }

    return createDLPackTensorMetal(mtlBuffer, width, height);
}

py::object
mtl_tensor_from_mach_port(unsigned int machPort, int width, int height) {
    IOSurfaceRef ioSurface = getIOSurfaceFromMachPort((mach_port_t)machPort);
    if (!ioSurface) {
        throw std::runtime_error("Failed to initialize IOSurface");
    }

    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    if (!device) {
        throw std::runtime_error("Failed to create Metal device");
    }

    // Calculate the length of the buffer
    size_t iosurface_width = IOSurfaceGetWidth(ioSurface);
    size_t iosurface_height = IOSurfaceGetHeight(ioSurface);
    assert(iosurface_width == width);
    assert(iosurface_height == height);
    size_t bytesPerRow = IOSurfaceGetBytesPerRow(ioSurface);
    size_t length = bytesPerRow * height;

    // Create a Metal buffer from the IOSurface
    // todo: lock / unlock / copy?
    id<MTLBuffer> mtlBuffer = [device
        newBufferWithBytesNoCopy:IOSurfaceGetBaseAddress(ioSurface)
                          length:length
                         options:
                             MTLResourceStorageModeShared // MTLResourceStorageModePrivate
                     deallocator:^(void *_Nonnull pointer, NSUInteger length) {
                       // deallocator can be used to free the memory
                       NSLog(
                           @"Deallocator called for buffer with pointer: %p, "
                           @"length: %lu",
                           pointer,
                           (unsigned long)length
                       );
                     }];

    if (!mtlBuffer) {
        throw std::runtime_error("Failed to create Metal buffer from IOSurface"
        );
    }

    DLManagedTensor *tensor = createDLPackTensorMetal(mtlBuffer, width, height);

#if USE_CUSTOM_DL_PACK_TENSOR
    return py::reinterpret_steal<py::object>(torchTensorFromDLPack(tensor));
#else
    return py::reinterpret_steal<py::object>(PyCapsule_New(
        tensor,
        "dltensor",
        [](PyObject *capsule) {
            DLManagedTensor *tensor =
                (DLManagedTensor *)PyCapsule_GetPointer(capsule, "dltensor");
            tensor->deleter(tensor);
        }
    ));
#endif
}
