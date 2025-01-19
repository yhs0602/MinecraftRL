#ifndef __IPC_H__
#define __IPC_H__

namespace py = pybind11;
py::object
initialize_from_mach_port(unsigned int machPort, int width, int height);
py::capsule mtl_tensor_from_cuda_mem_handle(
    const char *cuda_ipc_handle, int width, int height
);

py::capsule
mtl_dlpack_from_mach_port(unsigned int machPort, int width, int height);

#endif