package com.example.voltloop

// Simple stub for AvatarUpload on Android and iOS since createHttpClient is common.
// No expect/actual needed if Ktor HTTP client is available in commonMain! 
// Wait, the error earlier was:
// > Expected ImagePicker has no actual declaration in module <VOLTLOOP:composeApp> for Native
// > Expected rememberImagePicker has no actual declaration in module <VOLTLOOP:composeApp> for Native
// This means AvatarUpload actually compiled fine for commonMain!
