/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import Foundation

public struct Endpoint: Hashable {
    public let path: String
    public let queryItems: [URLQueryItem]?
    public let protocolType: ProtocolType?
    public let host: String
    public let port: Int16
    
    //TODO: figure out if port is provided by any smithy trait
    public init(host: String,
                path: String = "/",
                port: Int16 = 443,
                queryItems: [URLQueryItem]? = nil,
                protocolType: ProtocolType? = .https) {
        self.host = host
        self.path = path
        self.port = port
        self.queryItems = queryItems
        self.protocolType = protocolType
    }
}

public extension Endpoint {
    // We still have to keep 'url' as an optional, since we're
    // dealing with dynamic components that could be invalid.
    var url: URL? {
        var components = URLComponents()
        components.scheme = protocolType?.rawValue
        components.host = host
        components.path = path
        components.queryItems = queryItems
        //for local development
        components.port = Int(port)

        return components.url
    }
    
    var urlString: String {
        return host + "/"  + path
    }
}