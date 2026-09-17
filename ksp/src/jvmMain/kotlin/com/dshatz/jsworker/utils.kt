package com.dshatz.jsworker

import com.dshatz.jsworker.model.ParamInfo
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

fun List<KSValueParameter>.toParamList(): List<ParamInfo> {
    return map {
        ParamInfo(it.name!!.asString(), it.type.toTypeName())
    }
}

fun ClassName.withSuffix(suffix: String): ClassName {
    return ClassName(packageName, simpleName + suffix)
}

fun TypeSpec.Builder.addDataClassProps(
    props: List<ParamInfo>
): TypeSpec.Builder {
    val constructor = FunSpec.constructorBuilder()
    props.forEach {
        constructor.addParameter(ParameterSpec(it.name, it.type))
        addProperty(PropertySpec.builder(it.name, it.type).initializer(it.name).build())
    }
    return primaryConstructor(constructor.build())
}

fun buildDataClassOrObject(
    name: ClassName,
    props: List<ParamInfo>
): TypeSpec.Builder {
    val builder = if (props.isEmpty()) TypeSpec.objectBuilder(name) else TypeSpec.classBuilder(name)
    builder.addModifiers(KModifier.DATA)
    if (props.isNotEmpty()) {
        builder.addDataClassProps(props)
    }
    return builder
}