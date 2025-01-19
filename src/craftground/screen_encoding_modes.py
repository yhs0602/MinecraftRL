from enum import Enum


class ScreenEncodingMode(Enum):
    RAW = 0
    PNG = 1
    ZEROCOPY = 2
    JAX = 3
