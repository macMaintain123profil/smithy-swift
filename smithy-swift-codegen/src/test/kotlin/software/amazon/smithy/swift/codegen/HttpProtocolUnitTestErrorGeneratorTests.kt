/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test

class HttpProtocolUnitTestErrorGeneratorTests : HttpProtocolUnitTestResponseGeneratorTests() {

    @Test
    fun `it creates error test for simple error with no payload`() {
        val contents = getTestFileContents("example", "GreetingWithErrorsErrorTest.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()

        val expectedContents =
"""
class GreetingWithErrorsFooErrorTest: HttpResponseTestBase {
    let host = "my-api.us-east-2.amazonaws.com"
    /// Serializes the X-Amzn-ErrorType header. For an example service, see Amazon EKS.
    func testRestJsonFooErrorUsingXAmznErrorType() {
        do {
            guard let httpResponse = buildHttpResponse(
                code: 500,
                headers: [
                    "X-Amzn-Errortype": "FooError"
                ],
                host: host
            ) else {
                XCTFail("Something is wrong with the created http response")
                return
            }

            let greetingWithErrorsError = try GreetingWithErrorsError(httpResponse: httpResponse)

            if case .fooError(let actual) = greetingWithErrorsError {

                let expected = FooError(
                )
                XCTAssertEqual(actual._statusCode, HttpStatusCode(rawValue: 500))
            } else {
                XCTFail("The deserialized error type does not match expected type")
            }

        } catch let err {
            XCTFail(err.localizedDescription)
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it creates error test for complex error with payload`() {
        val contents = getTestFileContents("example", "GreetingWithErrorsErrorTest.swift", newTestContext.manifest)
        contents.shouldSyntacticSanityCheck()

        val expectedContents =
"""
class GreetingWithErrorsComplexErrorTest: HttpResponseTestBase {
    let host = "my-api.us-east-2.amazonaws.com"
    /// Serializes a complex error with no message member
    func testRestJsonComplexErrorWithNoMessage() {
        do {
            guard let httpResponse = buildHttpResponse(
                code: 403,
                headers: [
                    "Content-Type": "application/json",
                    "X-Amzn-Errortype": "ComplexError",
                    "X-Header": "Header"
                ],
                content: HttpBody.data(""${'"'}
                {
                    "TopLevel": "Top level",
                    "Nested": {
                        "Fooooo": "bar"
                    }
                }
                ""${'"'}.data(using: .utf8)),
                host: host
            ) else {
                XCTFail("Something is wrong with the created http response")
                return
            }

            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .secondsSince1970
            let greetingWithErrorsError = try GreetingWithErrorsError(httpResponse: httpResponse, decoder: decoder)

            if case .complexError(let actual) = greetingWithErrorsError {

                let expected = ComplexError(
                    header: "Header",
                    nested: ComplexNestedErrorData(
                        foo: "bar"
                    ),
                    topLevel: "Top level"
                )
                XCTAssertEqual(actual._statusCode, HttpStatusCode(rawValue: 403))
                XCTAssertEqual(expected.header, actual.header)
                XCTAssertEqual(expected.topLevel, actual.topLevel)
                XCTAssertEqual(expected.nested, actual.nested)
            } else {
                XCTFail("The deserialized error type does not match expected type")
            }

        } catch let err {
            XCTFail(err.localizedDescription)
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }
}