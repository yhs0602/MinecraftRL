// Sad news: PyTorch does not support from_dlpack for Metal tensors.
// Therefore, we should create a OpenCL dlpack tensor from the IOSurface.
#include "ipc_apple.h"
#if USE_OPENCL_DL_PACK_TENSOR 
#include <CoreGraphics/CoreGraphics.h>
#include <IOSurface/IOSurface.h>
#import <Metal/Metal.h>
#define GL_SILENCE_DEPRECATION

#include <OpenCL/cl_ext.h>
#include <OpenCL/opencl.h>
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl.h>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

static bool initializedOpenCL = false;

static cl_context createOpenCLContext() {
    if (initializedOpenCL) {
        throw std::runtime_error("OpenCL context is already initialized.");
    }

    cl_int err;

    // 1. Check available platforms
    cl_uint numPlatforms;
    err = clGetPlatformIDs(0, nullptr, &numPlatforms);
    if (err != CL_SUCCESS || numPlatforms == 0) {
        throw std::runtime_error("Failed to find any OpenCL platforms.");
    }

    std::vector<cl_platform_id> platforms(numPlatforms);
    err = clGetPlatformIDs(numPlatforms, platforms.data(), nullptr);
    if (err != CL_SUCCESS) {
        throw std::runtime_error("Failed to get OpenCL platform IDs.");
    }

    // 2. Check available devices, select the first GPU device
    cl_platform_id selectedPlatform = platforms[0]; // select the first platform
    cl_uint numDevices;
    err = clGetDeviceIDs(
        selectedPlatform, CL_DEVICE_TYPE_GPU, 0, nullptr, &numDevices
    );
    if (err != CL_SUCCESS || numDevices == 0) {
        throw std::runtime_error("Failed to find any OpenCL devices.");
    }

    std::vector<cl_device_id> devices(numDevices);
    err = clGetDeviceIDs(
        selectedPlatform,
        CL_DEVICE_TYPE_GPU,
        numDevices,
        devices.data(),
        nullptr
    );
    if (err != CL_SUCCESS) {
        throw std::runtime_error("Failed to get OpenCL device IDs.");
    }

    // 3. Create OpenCL context
    cl_context_properties properties[] = {
        CL_CONTEXT_PLATFORM, (cl_context_properties)selectedPlatform, 0
    };

    cl_context context =
        clCreateContext(properties, 1, &devices[0], nullptr, nullptr, &err);
    if (err != CL_SUCCESS) {
        throw std::runtime_error("Failed to create OpenCL context.");
    }
    initializedOpenCL = true;
    return context;
}

DLManagedTensor *createDLPackTensorFromOpenCL(
    cl_context context, IOSurfaceRef ioSurface, size_t width, size_t height
) {
    // create opencl image
    cl_image_format format = {
        CL_RGBA,      // rgba channel order
        CL_UNORM_INT8 // 8-bit unsigned normalized integer
    };

    cl_int errcode;
    cl_mem clBuffer = clCreateImageFromIOSurface2DAPPLE(
        context, CL_MEM_READ_WRITE, &format, width, height, ioSurface, &errcode
    );

    if (!clBuffer || errcode != CL_SUCCESS) {
        throw std::runtime_error(
            "Failed to create OpenCL image from IOSurface: " +
            std::to_string(errcode)
        );
    }

    DLManagedTensor *tensor =
        (DLManagedTensor *)malloc(sizeof(DLManagedTensor));

    tensor->dl_tensor.data =
        reinterpret_cast<void *>(clBuffer); // set cl_mem as data
    tensor->dl_tensor.ndim = 3;             // 3D tensor (H x W x C)
    tensor->dl_tensor.shape = (int64_t *)malloc(3 * sizeof(int64_t));
    tensor->dl_tensor.shape[0] = height;
    tensor->dl_tensor.shape[1] = width;
    tensor->dl_tensor.shape[2] = 4;      // RGBA
    tensor->dl_tensor.strides = nullptr; // Dense layout
    tensor->dl_tensor.byte_offset = 0;

    tensor->dl_tensor.dtype = {kDLUInt, 8, 1}; // Unsigned 8-bit integer
    tensor->dl_tensor.device = {kDLOpenCL, 0}; // OpenCL device

    // Set memory deleter
    tensor->manager_ctx = new cl_mem{clBuffer}; // cl_mem context
    tensor->deleter = [](DLManagedTensor *self) {
        cl_mem *buffer = reinterpret_cast<cl_mem *>(self->manager_ctx);
        clReleaseMemObject(*buffer); // OpenCL release
        delete buffer;

        free(self->dl_tensor.shape); // release shape
        free(self);                  // DLManagedTensor release
    };

    return tensor;
}
#endif