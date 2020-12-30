/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.swift.codegen

import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model

/**
 * Plugin to trigger Swift code generation.
 */
class SwiftCodegenPlugin : SmithyBuildPlugin {

    companion object {
        /**
         * Creates a Kotlin symbol provider.
         * @param model The model to generate symbols for
         * @param namespace The root package name (e.g. com.foo.bar). All symbols will be generated as part of this
         * package (or as a child of it)
         * @return Returns the created provider
         */
        fun createSymbolProvider(model: Model, namespace: String): SymbolProvider = SymbolVisitor(model, namespace)
    }

    override fun getName(): String = "swift-codegen"

    override fun execute(context: PluginContext) {
        println("executing swift codegen")

        CodegenVisitor(context).execute()
    }
}