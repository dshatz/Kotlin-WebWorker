package com.dshatz.jsworker

import com.dshatz.jsworker.model.ParamInfo
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

fun List<KSValueParameter>.toParamList(): List<ParamInfo> {
    return map {
//        val isTransfer = it.annotations.any { it.annotationType.toTypeName() == Types.Annotations.TransferOwnership } || it.type.toTypeName() == ClassName("org.khronos.webgl", "Int8Array")
        val propType = it.type.toTypeName()
        ParamInfo(it.name!!.asString(), propType)
    }
}

fun ClassName.withSuffix(suffix: String): ClassName {
    return ClassName(packageName, simpleName + suffix)
}

fun TypeSpec.Builder.addDataClassProps(
    props: List<ParamInfo>,
    override: Set<String> = emptySet()
): TypeSpec.Builder {
    val constructor = FunSpec.constructorBuilder()
    props.forEach {
        val propSpec = PropertySpec.builder(it.name, it.type)

        propSpec.addAnnotation(
            AnnotationSpec.builder(ClassName("kotlinx.serialization", "Contextual"))
                .build()
        )
        constructor.addParameter(ParameterSpec(it.name, it.type))
        val prop = propSpec.initializer(it.name)
        if (it.name in override) prop.addModifiers(KModifier.OVERRIDE)
        addProperty(prop.build())
    }
    return primaryConstructor(constructor.build())
}

fun buildDataClassOrObject(
    name: ClassName,
    props: List<ParamInfo>,
    override: Set<String> = emptySet()
): TypeSpec.Builder {
    val builder = if (props.isEmpty()) TypeSpec.objectBuilder(name) else TypeSpec.classBuilder(name)
    builder.addModifiers(KModifier.DATA)
    if (props.isNotEmpty()) {
        builder.addDataClassProps(props, override)
    }
    return builder
}
