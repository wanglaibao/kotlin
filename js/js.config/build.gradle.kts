plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":compiler:config"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
