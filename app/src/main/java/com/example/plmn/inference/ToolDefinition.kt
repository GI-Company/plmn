package com.example.plmn.inference

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String = "{}"
)
