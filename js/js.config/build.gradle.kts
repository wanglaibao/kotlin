plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":compiler:frontend.common"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
