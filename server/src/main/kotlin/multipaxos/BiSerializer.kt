package multipaxos

import com.google.protobuf.ByteString

interface BiSerializer<Type, Serialization> {
    fun serialize(obj: Type): Serialization
    fun deserialize(serialization: Serialization): Type
}

interface ByteStringBiSerializer<Type> : BiSerializer<Type, ByteString> {
    operator fun invoke(obj: Type): ByteString = serialize(obj)
    operator fun invoke(serialization: ByteString): Type = deserialize(serialization)
}