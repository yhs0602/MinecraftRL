from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class ItemStack(_message.Message):
    __slots__ = ("raw_id", "translation_key", "count", "durability", "max_durability")
    RAW_ID_FIELD_NUMBER: _ClassVar[int]
    TRANSLATION_KEY_FIELD_NUMBER: _ClassVar[int]
    COUNT_FIELD_NUMBER: _ClassVar[int]
    DURABILITY_FIELD_NUMBER: _ClassVar[int]
    MAX_DURABILITY_FIELD_NUMBER: _ClassVar[int]
    raw_id: int
    translation_key: str
    count: int
    durability: int
    max_durability: int
    def __init__(self, raw_id: _Optional[int] = ..., translation_key: _Optional[str] = ..., count: _Optional[int] = ..., durability: _Optional[int] = ..., max_durability: _Optional[int] = ...) -> None: ...

class BlockInfo(_message.Message):
    __slots__ = ("x", "y", "z", "translation_key")
    X_FIELD_NUMBER: _ClassVar[int]
    Y_FIELD_NUMBER: _ClassVar[int]
    Z_FIELD_NUMBER: _ClassVar[int]
    TRANSLATION_KEY_FIELD_NUMBER: _ClassVar[int]
    x: int
    y: int
    z: int
    translation_key: str
    def __init__(self, x: _Optional[int] = ..., y: _Optional[int] = ..., z: _Optional[int] = ..., translation_key: _Optional[str] = ...) -> None: ...

class EntityInfo(_message.Message):
    __slots__ = ("unique_name", "translation_key", "x", "y", "z", "yaw", "pitch", "health")
    UNIQUE_NAME_FIELD_NUMBER: _ClassVar[int]
    TRANSLATION_KEY_FIELD_NUMBER: _ClassVar[int]
    X_FIELD_NUMBER: _ClassVar[int]
    Y_FIELD_NUMBER: _ClassVar[int]
    Z_FIELD_NUMBER: _ClassVar[int]
    YAW_FIELD_NUMBER: _ClassVar[int]
    PITCH_FIELD_NUMBER: _ClassVar[int]
    HEALTH_FIELD_NUMBER: _ClassVar[int]
    unique_name: str
    translation_key: str
    x: float
    y: float
    z: float
    yaw: float
    pitch: float
    health: float
    def __init__(self, unique_name: _Optional[str] = ..., translation_key: _Optional[str] = ..., x: _Optional[float] = ..., y: _Optional[float] = ..., z: _Optional[float] = ..., yaw: _Optional[float] = ..., pitch: _Optional[float] = ..., health: _Optional[float] = ...) -> None: ...

class HitResult(_message.Message):
    __slots__ = ("type", "target_block", "target_entity")
    class Type(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
        __slots__ = ()
        MISS: _ClassVar[HitResult.Type]
        BLOCK: _ClassVar[HitResult.Type]
        ENTITY: _ClassVar[HitResult.Type]
    MISS: HitResult.Type
    BLOCK: HitResult.Type
    ENTITY: HitResult.Type
    TYPE_FIELD_NUMBER: _ClassVar[int]
    TARGET_BLOCK_FIELD_NUMBER: _ClassVar[int]
    TARGET_ENTITY_FIELD_NUMBER: _ClassVar[int]
    type: HitResult.Type
    target_block: BlockInfo
    target_entity: EntityInfo
    def __init__(self, type: _Optional[_Union[HitResult.Type, str]] = ..., target_block: _Optional[_Union[BlockInfo, _Mapping]] = ..., target_entity: _Optional[_Union[EntityInfo, _Mapping]] = ...) -> None: ...

class StatusEffect(_message.Message):
    __slots__ = ("translation_key", "duration", "amplifier")
    TRANSLATION_KEY_FIELD_NUMBER: _ClassVar[int]
    DURATION_FIELD_NUMBER: _ClassVar[int]
    AMPLIFIER_FIELD_NUMBER: _ClassVar[int]
    translation_key: str
    duration: int
    amplifier: int
    def __init__(self, translation_key: _Optional[str] = ..., duration: _Optional[int] = ..., amplifier: _Optional[int] = ...) -> None: ...

class SoundEntry(_message.Message):
    __slots__ = ("translate_key", "age", "x", "y", "z")
    TRANSLATE_KEY_FIELD_NUMBER: _ClassVar[int]
    AGE_FIELD_NUMBER: _ClassVar[int]
    X_FIELD_NUMBER: _ClassVar[int]
    Y_FIELD_NUMBER: _ClassVar[int]
    Z_FIELD_NUMBER: _ClassVar[int]
    translate_key: str
    age: int
    x: float
    y: float
    z: float
    def __init__(self, translate_key: _Optional[str] = ..., age: _Optional[int] = ..., x: _Optional[float] = ..., y: _Optional[float] = ..., z: _Optional[float] = ...) -> None: ...

class EntitiesWithinDistance(_message.Message):
    __slots__ = ("entities",)
    ENTITIES_FIELD_NUMBER: _ClassVar[int]
    entities: _containers.RepeatedCompositeFieldContainer[EntityInfo]
    def __init__(self, entities: _Optional[_Iterable[_Union[EntityInfo, _Mapping]]] = ...) -> None: ...

class ChatMessageInfo(_message.Message):
    __slots__ = ("added_time", "message", "indicator")
    ADDED_TIME_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    INDICATOR_FIELD_NUMBER: _ClassVar[int]
    added_time: int
    message: str
    indicator: str
    def __init__(self, added_time: _Optional[int] = ..., message: _Optional[str] = ..., indicator: _Optional[str] = ...) -> None: ...

class BiomeInfo(_message.Message):
    __slots__ = ("biome_name", "center_x", "center_y", "center_z")
    BIOME_NAME_FIELD_NUMBER: _ClassVar[int]
    CENTER_X_FIELD_NUMBER: _ClassVar[int]
    CENTER_Y_FIELD_NUMBER: _ClassVar[int]
    CENTER_Z_FIELD_NUMBER: _ClassVar[int]
    biome_name: str
    center_x: int
    center_y: int
    center_z: int
    def __init__(self, biome_name: _Optional[str] = ..., center_x: _Optional[int] = ..., center_y: _Optional[int] = ..., center_z: _Optional[int] = ...) -> None: ...

class NearbyBiome(_message.Message):
    __slots__ = ("biome_name", "x", "y", "z")
    BIOME_NAME_FIELD_NUMBER: _ClassVar[int]
    X_FIELD_NUMBER: _ClassVar[int]
    Y_FIELD_NUMBER: _ClassVar[int]
    Z_FIELD_NUMBER: _ClassVar[int]
    biome_name: str
    x: int
    y: int
    z: int
    def __init__(self, biome_name: _Optional[str] = ..., x: _Optional[int] = ..., y: _Optional[int] = ..., z: _Optional[int] = ...) -> None: ...

class HeightInfo(_message.Message):
    __slots__ = ("x", "z", "height", "block_name")
    X_FIELD_NUMBER: _ClassVar[int]
    Z_FIELD_NUMBER: _ClassVar[int]
    HEIGHT_FIELD_NUMBER: _ClassVar[int]
    BLOCK_NAME_FIELD_NUMBER: _ClassVar[int]
    x: int
    z: int
    height: int
    block_name: str
    def __init__(self, x: _Optional[int] = ..., z: _Optional[int] = ..., height: _Optional[int] = ..., block_name: _Optional[str] = ...) -> None: ...

class ObservationSpaceMessage(_message.Message):
    __slots__ = ("image", "x", "y", "z", "yaw", "pitch", "health", "food_level", "saturation_level", "is_dead", "inventory", "raycast_result", "sound_subtitles", "status_effects", "killed_statistics", "mined_statistics", "misc_statistics", "visible_entities", "surrounding_entities", "bobber_thrown", "experience", "world_time", "last_death_message", "image_2", "surrounding_blocks", "eye_in_block", "suffocating", "chat_messages", "biome_info", "nearby_biomes", "submerged_in_water", "is_in_lava", "submerged_in_lava", "height_info", "is_on_ground", "is_touching_water", "ipc_handle")
    class KilledStatisticsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: int
        def __init__(self, key: _Optional[str] = ..., value: _Optional[int] = ...) -> None: ...
    class MinedStatisticsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: int
        def __init__(self, key: _Optional[str] = ..., value: _Optional[int] = ...) -> None: ...
    class MiscStatisticsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: int
        def __init__(self, key: _Optional[str] = ..., value: _Optional[int] = ...) -> None: ...
    class SurroundingEntitiesEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: int
        value: EntitiesWithinDistance
        def __init__(self, key: _Optional[int] = ..., value: _Optional[_Union[EntitiesWithinDistance, _Mapping]] = ...) -> None: ...
    IMAGE_FIELD_NUMBER: _ClassVar[int]
    X_FIELD_NUMBER: _ClassVar[int]
    Y_FIELD_NUMBER: _ClassVar[int]
    Z_FIELD_NUMBER: _ClassVar[int]
    YAW_FIELD_NUMBER: _ClassVar[int]
    PITCH_FIELD_NUMBER: _ClassVar[int]
    HEALTH_FIELD_NUMBER: _ClassVar[int]
    FOOD_LEVEL_FIELD_NUMBER: _ClassVar[int]
    SATURATION_LEVEL_FIELD_NUMBER: _ClassVar[int]
    IS_DEAD_FIELD_NUMBER: _ClassVar[int]
    INVENTORY_FIELD_NUMBER: _ClassVar[int]
    RAYCAST_RESULT_FIELD_NUMBER: _ClassVar[int]
    SOUND_SUBTITLES_FIELD_NUMBER: _ClassVar[int]
    STATUS_EFFECTS_FIELD_NUMBER: _ClassVar[int]
    KILLED_STATISTICS_FIELD_NUMBER: _ClassVar[int]
    MINED_STATISTICS_FIELD_NUMBER: _ClassVar[int]
    MISC_STATISTICS_FIELD_NUMBER: _ClassVar[int]
    VISIBLE_ENTITIES_FIELD_NUMBER: _ClassVar[int]
    SURROUNDING_ENTITIES_FIELD_NUMBER: _ClassVar[int]
    BOBBER_THROWN_FIELD_NUMBER: _ClassVar[int]
    EXPERIENCE_FIELD_NUMBER: _ClassVar[int]
    WORLD_TIME_FIELD_NUMBER: _ClassVar[int]
    LAST_DEATH_MESSAGE_FIELD_NUMBER: _ClassVar[int]
    IMAGE_2_FIELD_NUMBER: _ClassVar[int]
    SURROUNDING_BLOCKS_FIELD_NUMBER: _ClassVar[int]
    EYE_IN_BLOCK_FIELD_NUMBER: _ClassVar[int]
    SUFFOCATING_FIELD_NUMBER: _ClassVar[int]
    CHAT_MESSAGES_FIELD_NUMBER: _ClassVar[int]
    BIOME_INFO_FIELD_NUMBER: _ClassVar[int]
    NEARBY_BIOMES_FIELD_NUMBER: _ClassVar[int]
    SUBMERGED_IN_WATER_FIELD_NUMBER: _ClassVar[int]
    IS_IN_LAVA_FIELD_NUMBER: _ClassVar[int]
    SUBMERGED_IN_LAVA_FIELD_NUMBER: _ClassVar[int]
    HEIGHT_INFO_FIELD_NUMBER: _ClassVar[int]
    IS_ON_GROUND_FIELD_NUMBER: _ClassVar[int]
    IS_TOUCHING_WATER_FIELD_NUMBER: _ClassVar[int]
    IPC_HANDLE_FIELD_NUMBER: _ClassVar[int]
    image: bytes
    x: float
    y: float
    z: float
    yaw: float
    pitch: float
    health: float
    food_level: float
    saturation_level: float
    is_dead: bool
    inventory: _containers.RepeatedCompositeFieldContainer[ItemStack]
    raycast_result: HitResult
    sound_subtitles: _containers.RepeatedCompositeFieldContainer[SoundEntry]
    status_effects: _containers.RepeatedCompositeFieldContainer[StatusEffect]
    killed_statistics: _containers.ScalarMap[str, int]
    mined_statistics: _containers.ScalarMap[str, int]
    misc_statistics: _containers.ScalarMap[str, int]
    visible_entities: _containers.RepeatedCompositeFieldContainer[EntityInfo]
    surrounding_entities: _containers.MessageMap[int, EntitiesWithinDistance]
    bobber_thrown: bool
    experience: int
    world_time: int
    last_death_message: str
    image_2: bytes
    surrounding_blocks: _containers.RepeatedCompositeFieldContainer[BlockInfo]
    eye_in_block: bool
    suffocating: bool
    chat_messages: _containers.RepeatedCompositeFieldContainer[ChatMessageInfo]
    biome_info: BiomeInfo
    nearby_biomes: _containers.RepeatedCompositeFieldContainer[NearbyBiome]
    submerged_in_water: bool
    is_in_lava: bool
    submerged_in_lava: bool
    height_info: _containers.RepeatedCompositeFieldContainer[HeightInfo]
    is_on_ground: bool
    is_touching_water: bool
    ipc_handle: bytes
    def __init__(self, image: _Optional[bytes] = ..., x: _Optional[float] = ..., y: _Optional[float] = ..., z: _Optional[float] = ..., yaw: _Optional[float] = ..., pitch: _Optional[float] = ..., health: _Optional[float] = ..., food_level: _Optional[float] = ..., saturation_level: _Optional[float] = ..., is_dead: bool = ..., inventory: _Optional[_Iterable[_Union[ItemStack, _Mapping]]] = ..., raycast_result: _Optional[_Union[HitResult, _Mapping]] = ..., sound_subtitles: _Optional[_Iterable[_Union[SoundEntry, _Mapping]]] = ..., status_effects: _Optional[_Iterable[_Union[StatusEffect, _Mapping]]] = ..., killed_statistics: _Optional[_Mapping[str, int]] = ..., mined_statistics: _Optional[_Mapping[str, int]] = ..., misc_statistics: _Optional[_Mapping[str, int]] = ..., visible_entities: _Optional[_Iterable[_Union[EntityInfo, _Mapping]]] = ..., surrounding_entities: _Optional[_Mapping[int, EntitiesWithinDistance]] = ..., bobber_thrown: bool = ..., experience: _Optional[int] = ..., world_time: _Optional[int] = ..., last_death_message: _Optional[str] = ..., image_2: _Optional[bytes] = ..., surrounding_blocks: _Optional[_Iterable[_Union[BlockInfo, _Mapping]]] = ..., eye_in_block: bool = ..., suffocating: bool = ..., chat_messages: _Optional[_Iterable[_Union[ChatMessageInfo, _Mapping]]] = ..., biome_info: _Optional[_Union[BiomeInfo, _Mapping]] = ..., nearby_biomes: _Optional[_Iterable[_Union[NearbyBiome, _Mapping]]] = ..., submerged_in_water: bool = ..., is_in_lava: bool = ..., submerged_in_lava: bool = ..., height_info: _Optional[_Iterable[_Union[HeightInfo, _Mapping]]] = ..., is_on_ground: bool = ..., is_touching_water: bool = ..., ipc_handle: _Optional[bytes] = ...) -> None: ...
