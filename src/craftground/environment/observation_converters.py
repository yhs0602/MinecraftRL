from enum import Enum
import io
from typing import TYPE_CHECKING, Optional, Tuple, Union

from environment.action_space_version import ActionSpaceVersion
from font import get_font
from print_with_time import print_with_time
from proto.observation_space_pb2 import ObservationSpaceMessage
from screen_encoding_modes import ScreenEncodingMode
import numpy as np
from PIL import Image, ImageDraw


class ObservationTensorType(Enum):
    NONE = 0
    CUDA_DLPACK = 1
    APPLE_TENSOR = 2
    JAX_NP = 3


if TYPE_CHECKING:
    import torch
    import jax.numpy as jnp

    TorchArrayType = torch.Tensor
    JaxArrayType = jnp.ndarray
else:
    try:
        import torch

        TorchArrayType = torch.Tensor
    except ImportError:
        TorchArrayType = None
    try:
        import jax
        import jax.numpy as jnp

        JaxArrayType = jnp.ndarray
    except ImportError:
        JaxArrayType = None
ImageOutputType = Union[np.ndarray, "TorchArrayType", "JaxArrayType"]


class ObservationConverter:
    def __init__(
        self,
        output_type: ScreenEncodingMode,
        is_binocular: bool = False,
        render_action: bool = False,
    ) -> None:
        self.output_type = output_type
        self.internal_type = ObservationTensorType.NONE
        self.last_observations = [None, None]
        self.last_images = [None, None]
        self.is_binocular = is_binocular
        self.render_alternating_eyes_counter = 0
        self.render_action = render_action

    def convert(
        self, observation: ObservationSpaceMessage
    ) -> Tuple[Optional[ImageOutputType]]:
        if self.output_type == ScreenEncodingMode.PNG:
            obs_1, obs_2 = None, None
            img_1, img_2 = None, None
            obs_1, img_1 = self.convert_png_observation(observation.image)
            if self.is_binocular:
                obs_2, img_2 = self.convert_png_observation(observation.image_2)
            self.last_observations[0], self.last_observations[1] = obs_1, obs_2
            self.last_images[0], self.last_images[1] = img_1, img_2
            return (obs_1, obs_2)
        elif self.output_type == ScreenEncodingMode.RAW:
            obs_1, obs_2 = None, None
            img_1, img_2 = None, None
            obs_1 = self.convert_raw_observation(observation.image)
            if self.is_binocular:
                obs_2 = self.convert_raw_observation(observation.image_2)
            self.last_observations[0], self.last_observations[1] = obs_1, obs_2
            return (obs_1, obs_2)
        elif self.output_type == ScreenEncodingMode.ZEROCOPY:
            if self.is_binocular:
                raise ValueError("Zerocopy mode does not support binocular vision")
            if self.internal_type == ObservationTensorType.NONE:
                self.initialize_zerocopy(observation.ipc_handle)
            if self.internal_type == ObservationTensorType.APPLE_TENSOR:
                obs_1 = self.last_observations[0].clone()[:, :, [2, 1, 0]].flip(0)
                return (obs_1, None)
            elif self.internal_type == ObservationTensorType.CUDA_DLPACK:
                obs_1 = self.observation_tensors[0].clone()[:, :, :3].flip(0)
                return (obs_1, None)
            else:
                raise ValueError(
                    f"Invalid internal type for output {self.output_type}: {self.internal_type}"
                )

        elif self.output_type == ScreenEncodingMode.JAX:
            if self.is_binocular:
                raise ValueError("JAX mode does not support binocular vision")
            if self.internal_type == ObservationTensorType.JAX_NP:
                return (self.last_observations[0], None)
            else:
                pass
            return self.convert_jax_zerocopy(observation)
        else:
            raise ValueError(f"Unknown output type: {self.output_type}")

    # when self.render_mode is None: no render is computed
    # when self.render_mode is "human": render returns None, envrionment is already being rendered on the screen
    # when self.render_mode is "rgb_array": render returns the image to be rendered
    # when self.render_mode is "ansi": render returns the text to be rendered
    # when self.render_mode is "rgb_array_list": render returns a list of images to be rendered
    # when self.render_mode is "rgb_tensor": render returns a torch tensor to be rendered
    def render(self):
        # select last_image and last_frame
        if self.render_mode is None:
            return None
        if self.render_mode == "human":
            # do not render anything
            return None
        if self.render_mode == "ansi":
            raise ValueError("Rendering mode ansi not supported")
        if self.render_mode == "rgb_array_list":
            raise ValueError("Rendering mode rgb_array_list not supported")

        if self.render_alternating_eyes:
            last_image = self.last_images[self.render_alternating_eyes_counter]
            last_rgb_frame = self.last_observations[
                self.render_alternating_eyes_counter
            ]
            self.render_alternating_eyes_counter = (
                1 - self.render_alternating_eyes_counter
            )
        else:
            last_image = self.last_images[0]
            last_rgb_frame = self.last_observations[0]
        if last_image is None and last_rgb_frame is None:
            return None

        if isinstance(last_rgb_frame, TorchArrayType) and (
            self.render_mode != "rgb_array_tensor" or self.render_action
        ):
            # drop the alpha channel and convert to numpy array
            last_rgb_frame = last_rgb_frame.cpu().numpy()
        if isinstance(last_rgb_frame, JaxArrayType):
            last_rgb_frame = jax.device_get(last_rgb_frame)  # type: ignore

        # last_rgb_frame: np.ndarray or torch.Tensor
        # last_image: PIL.Image.Image or None
        if self.render_action and self.last_action:
            if last_image is None:
                # it is inevitable to convert the tensor to numpy array
                last_image = Image.fromarray(last_rgb_frame)
            with self.csv_logger.profile("render_action"):
                draw = ImageDraw.Draw(last_image)
                if self.action_space_version == ActionSpaceVersion.V1_MINEDOJO:
                    text = self.action_to_symbol(self.last_action)
                elif self.action_space_version == ActionSpaceVersion.V2_MINERL_HUMAN:
                    text = self.action_v2_to_symbol(self.last_action)
                else:
                    raise ValueError(
                        f"Unknown action space version {self.action_space_version}"
                    )
                position = (0, 0)
                font = get_font()
                font_size = 8
                color = (255, 0, 0)
                draw.text(position, text, font=font, font_size=font_size, fill=color)
            return np.array(last_image)
        else:
            return last_rgb_frame

    def convert_png_observation(
        self, image_bytes: bytes
    ) -> Tuple[np.ndarray, Image.Image]:
        # decode png byte array to numpy array
        # Create a BytesIO object from the byte array
        with self.csv_logger.profile("convert_observation/decode_png"):
            bytes_io = io.BytesIO(image_bytes)
            # Use PIL to open the image from the BytesIO object
            img = Image.open(bytes_io).convert("RGB")
            # Flip y axis
            img = img.transpose(Image.FLIP_TOP_BOTTOM)

        with self.csv_logger.profile("convert_observation/convert_to_numpy"):
            # Convert the PIL image to a numpy array
            last_rgb_frame = np.array(img)
            arr = np.transpose(last_rgb_frame, (2, 1, 0))
            rgb_array_or_tensor = arr.astype(np.uint8)

        return rgb_array_or_tensor, img

    def convert_raw_observation(self, image_bytes: bytes) -> np.ndarray:
        # decode raw byte array to numpy array
        with self.csv_logger.profile("convert_observation/decode_raw"):
            last_rgb_frame = np.frombuffer(image_bytes, dtype=np.uint8).reshape(
                (self.initial_env.imageSizeY, self.initial_env.imageSizeX, 3)
            )
            # Flip y axis using np
            last_rgb_frame = np.flip(last_rgb_frame, axis=0)
            rgb_array_or_tensor = last_rgb_frame
            # arr = np.transpose(last_rgb_frame, (2, 1, 0))  # channels, width, height
        return rgb_array_or_tensor

    def initialize_zerocopy(self, ipc_handle: bytes):
        import torch
        from .craftground_native import initialize_from_mach_port  # type: ignore
        from .craftground_native import mtl_tensor_from_cuda_mem_handle  # type: ignore

        if len(ipc_handle) == 0:
            raise ValueError("No ipc handle found.")
        if len(ipc_handle) == 4:
            mach_port = int.from_bytes(ipc_handle, byteorder="little", signed=False)
            print_with_time(f"{mach_port=}")
            apple_dl_tensor = initialize_from_mach_port(
                mach_port, self.initial_env.imageSizeX, self.initial_env.imageSizeY
            )
            if apple_dl_tensor is None:
                raise ValueError(f"Failed to initialize from mach port {mach_port}.")
            # image_tensor = torch.utils.dlpack.from_dlpack(apple_dl_tensor)
            rgb_array_or_tensor = apple_dl_tensor
            print(rgb_array_or_tensor.shape)
            print(rgb_array_or_tensor.dtype)
            print(rgb_array_or_tensor.device)
            self.last_observations[0] = rgb_array_or_tensor
            # drop alpha, flip y axis, and clone
            self.observation_tensor_type = ObservationTensorType.APPLE_TENSOR
        else:
            import torch.utils.dlpack

            cuda_dl_tensor = mtl_tensor_from_cuda_mem_handle(
                ipc_handle,
                self.initial_env.imageSizeX,
                self.initial_env.imageSizeY,
            )
            if not cuda_dl_tensor:
                raise ValueError("Invalid DLPack capsule: None")
            rgb_array_or_tensor = torch.utils.dlpack.from_dlpack(cuda_dl_tensor)
            print(rgb_array_or_tensor.shape)
            print(rgb_array_or_tensor.dtype)
            print(rgb_array_or_tensor.device)
            print(f"{rgb_array_or_tensor.data_ptr()=}\n\n")
            self.last_observations[0] = rgb_array_or_tensor
            # drop alpha, flip y axis, and clone
            self.observation_tensor_type = ObservationTensorType.CUDA_DLPACK

    def convert_jax_observation(self, ipc_handle: bytes) -> "JaxArrayType":
        import jax.numpy as jnp
        from .craftground_native import mtl_dlpack_from_mach_port  # type: ignore
        from .craftground_native import mtl_tensor_from_cuda_mem_handle  # type: ignore

        if len(ipc_handle) == 0:
            raise ValueError("No ipc handle found.")
        if len(ipc_handle) == 4:
            mach_port = int.from_bytes(ipc_handle, byteorder="little", signed=False)
            print_with_time(f"{mach_port=}")
            dlpack_capsule = mtl_dlpack_from_mach_port(
                mach_port, self.initial_env.imageSizeX, self.initial_env.imageSizeY
            )
            if not dlpack_capsule:
                raise ValueError(f"Failed to initialize from mach port {ipc_handle}.")
            jax_image = jnp.from_dlpack(cuda_dlpack)
            # image_tensor = torch.utils.dlpack.from_dlpack(apple_dl_tensor)
            rgb_array_or_tensor = jax_image
            print(rgb_array_or_tensor.shape)
            print(rgb_array_or_tensor.dtype)
            print(rgb_array_or_tensor.device())
            self.observation_tensors[0] = rgb_array_or_tensor
            # drop alpha, flip y axis, and clone
            rgb_array_or_tensor = rgb_array_or_tensor.clone()[:, :, [2, 1, 0]].flip(0)
            self.observation_tensor_type = ObservationTensorType.JAX_NP
            return rgb_array_or_tensor
        else:
            cuda_dlpack = mtl_tensor_from_cuda_mem_handle(
                ipc_handle,
                self.initial_env.imageSizeX,
                self.initial_env.imageSizeY,
            )
            if not cuda_dlpack:
                raise ValueError("Invalid DLPack capsule: None")
            jax_image = jnp.from_dlpack(cuda_dlpack)
            rgb_array_or_tensor = jax_image
            rgb_array_or_tensor = rgb_array_or_tensor.clone()[:, :, [2, 1, 0]].flip(0)
            self.observation_tensor_type = ObservationTensorType.JAX_NP
            return jax_image, None
