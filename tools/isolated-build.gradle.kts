// Preserve published APKs held open by Windows while building a new version.
allprojects {
    if (name == "app") layout.buildDirectory.set(file("build-0.3.3"))
}
