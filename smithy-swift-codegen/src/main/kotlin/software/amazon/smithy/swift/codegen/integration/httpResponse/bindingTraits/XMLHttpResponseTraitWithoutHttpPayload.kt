/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen.integration.httpResponse.bindingTraits

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.HttpQueryTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.swift.codegen.ClientRuntimeTypes
import software.amazon.smithy.swift.codegen.SwiftWriter
import software.amazon.smithy.swift.codegen.declareSection
import software.amazon.smithy.swift.codegen.integration.HttpBindingDescriptor
import software.amazon.smithy.swift.codegen.integration.ProtocolGenerator
import software.amazon.smithy.swift.codegen.integration.httpResponse.HttpResponseBindingRenderable
import software.amazon.smithy.swift.codegen.integration.serde.readwrite.isRPCBound
import software.amazon.smithy.swift.codegen.integration.serde.xml.MemberShapeDecodeXMLGenerator
import software.amazon.smithy.swift.codegen.model.targetOrSelf

class XMLHttpResponseTraitWithoutHttpPayload(
    val ctx: ProtocolGenerator.GenerationContext,
    val responseBindings: List<HttpBindingDescriptor>,
    val outputShape: Shape,
    val writer: SwiftWriter
) : HttpResponseBindingRenderable {
    override fun render() {
        val bodyMembers = responseBindings.filter { it.location == HttpBinding.Location.DOCUMENT }
        val bodyMembersWithoutQueryTrait = bodyMembers
            .filter { !it.member.hasTrait(HttpQueryTrait::class.java) }
            .toMutableSet()
        val streamingMember = bodyMembers.firstOrNull { it.member.targetOrSelf(ctx.model).hasTrait(StreamingTrait::class.java) }
        if (streamingMember != null) {
            val initialResponseMembers = bodyMembers.filter {
                val targetShape = it.member.targetOrSelf(ctx.model)
                targetShape?.hasTrait(StreamingTrait::class.java) == false
            }.toSet()
            writeStreamingMember(streamingMember, initialResponseMembers)
        } else if (bodyMembersWithoutQueryTrait.isNotEmpty()) {
            writeNonStreamingMembers(bodyMembersWithoutQueryTrait)
        }
    }

    fun writeStreamingMember(streamingMember: HttpBindingDescriptor, initialResponseMembers: Set<HttpBindingDescriptor>) {
        val shape = ctx.model.expectShape(streamingMember.member.target)
        val symbol = ctx.symbolProvider.toSymbol(shape)
        val memberName = ctx.symbolProvider.toMemberName(streamingMember.member)
        when (shape.type) {
            ShapeType.UNION -> {
                writer.openBlock("if case let .stream(stream) = httpResponse.body, let responseDecoder = decoder {", "} else {") {
                    writer.declareSection(HttpResponseTraitWithHttpPayload.MessageDecoderSectionId) {
                        write("let messageDecoder: \$D", ClientRuntimeTypes.EventStream.MessageDecoder)
                    }
                    writer.write(
                        "let decoderStream = \$L<\$N>(stream: stream, messageDecoder: messageDecoder, unmarshalClosure: xmlUnmarshalClosure())",
                        ClientRuntimeTypes.EventStream.MessageDecoderStream,
                        symbol
                    )
                    writer.write("self.\$L = decoderStream.toAsyncStream()", memberName)
                    if (ctx.service.isRPCBound && initialResponseMembers.isNotEmpty()) {
                        writeInitialResponseMembers(initialResponseMembers)
                    }
                }
                writer.indent()
                writer.write("self.\$L = nil", memberName).closeBlock("}")
            }
            ShapeType.BLOB -> {
                writer.write("switch httpResponse.body {")
                    .write("case .data(let data):")
                    .indent()
                writer.write("self.\$L = .data(data)", memberName)

                // For binary streams, we need to set the member to the stream directly.
                // this allows us to stream the data directly to the user
                // without having to buffer it in memory.
                writer.dedent()
                    .write("case .stream(let stream):")
                    .indent()
                writer.write("self.\$L = .stream(stream)", memberName)
                writer.dedent()
                    .write("case .noStream:")
                    .indent()
                    .write("self.\$L = nil", memberName).closeBlock("}")
            }
            else -> {
                throw CodegenException("member shape ${streamingMember.member} cannot stream")
            }
        }
    }

    fun writeNonStreamingMembers(members: Set<HttpBindingDescriptor>) {
        members.sortedBy { it.memberName }.forEach {
            MemberShapeDecodeXMLGenerator(ctx, writer, outputShape).render(it.member)
        }
    }

    private fun writeInitialResponseMembers(initialResponseMembers: Set<HttpBindingDescriptor>) {
        writer.apply {
            write("if let initialDataWithoutHttp = await messageDecoder.awaitInitialResponse() {")
            indent()
            write("let decoder = JSONDecoder()")
            write("do {")
            indent()
            write("let response = try decoder.decode([String: String].self, from: initialDataWithoutHttp)")
            initialResponseMembers.forEach { responseMember ->
                val responseMemberName = ctx.symbolProvider.toMemberName(responseMember.member)
                write("self.$responseMemberName = response[\"$responseMemberName\"].map { value in KinesisClientTypes.Tag(value: value) }")
            }
            dedent()
            write("} catch {")
            indent()
            write("print(\"Error decoding JSON: \\(error)\")")
            initialResponseMembers.forEach { responseMember ->
                val responseMemberName = ctx.symbolProvider.toMemberName(responseMember.member)
                write("self.$responseMemberName = nil")
            }
            dedent()
            write("}")
            dedent()
            write("} else {")
            indent()
            initialResponseMembers.forEach { responseMember ->
                val responseMemberName = ctx.symbolProvider.toMemberName(responseMember.member)
                write("self.$responseMemberName = nil")
            }
            dedent()
            write("}")
        }
    }
}
