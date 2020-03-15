plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":core:deserialization"))
    compile(project(":compiler:config"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
