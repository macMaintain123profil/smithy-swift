//
// Copyright Amazon.com Inc. or its affiliates.
// All Rights Reserved.
//
// SPDX-License-Identifier: Apache-2.0
//
import AwsCommonRuntimeKit
import class Foundation.FileHandle

public enum ByteStream {
    case data(Data?)
    case stream(Stream)
    case noStream

    // Read Data
    public func readData() async throws -> Data? {
        switch self {
        case .data(let data):
            return data
        case .stream(let stream):
            if stream.isSeekable {
                try stream.seek(toOffset: 0)
            }
            return try await stream.readToEndAsync()
        case .noStream:
            return nil
        }
    }
}

extension ByteStream: Equatable {
    public static func ==(lhs: ByteStream, rhs: ByteStream) -> Bool {
        switch (lhs, rhs) {
        case (.data(let lhsData), .data(let rhsData)):
            return lhsData == rhsData
        case (.stream(let lhsStream), .stream(let rhsStream)):
            return lhsStream === rhsStream
        case (.noStream, .noStream):
            return true
        default:
            return false
        }
    }
}

extension ByteStream: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .data(nil)
        } else {
            let data = try container.decode(Data.self)
            self = .data(data)
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .data(let data):
            try container.encode(data)
        case .stream:
            throw EncodingError.invalidValue(
                self,
                EncodingError.Context(
                    codingPath: encoder.codingPath,
                    debugDescription: "Cannot encode a stream."
                )
            )
        case .noStream:
            try container.encodeNil()
        }
    }
}

extension ByteStream {

    // Static property for an empty ByteStream
    public static var empty: ByteStream {
        .data(nil)
    }

    // Returns true if the byte stream is empty
    public var isEmpty: Bool {
        switch self {
        case .data(let data):
            return data?.isEmpty ?? true
        case .stream(let stream):
            return stream.isEmpty
        case .noStream:
            return true
        }
    }
}

extension ByteStream: CustomDebugStringConvertible {

    public var debugDescription: String {
        switch self {
        case .data(let data):
            return data?.description ?? "nil (Data)"
        case .stream(let stream):
            if stream.isSeekable {
                let currentPosition = stream.position
                defer { try? stream.seek(toOffset: currentPosition) }
                if let data = try? stream.readToEnd() {
                    return data.description
                } else {
                    return "Stream not readable"
                }
            } else {
                return "Stream (non-seekable, Position: \(stream.position), Length: \(stream.length ?? -1))"
            }
        case .noStream:
            return "nil"
        }
    }
}

extension ByteStream {

    /// Returns ByteStream from a FileHandle object.
    /// - Parameter fileHandle: FileHandle object to be converted to ByteStream.
    /// - Returns: ByteStream representation of the FileHandle object.
    public static func from(fileHandle: FileHandle) -> ByteStream {
        return .stream(FileStream(fileHandle: fileHandle))
    }
}
