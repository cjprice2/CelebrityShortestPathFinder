@echo off

jpackage ^
  --type exe ^
  --input target ^
  --name ActorShortestPathApp ^
  --main-jar ActorShortestPathApp-1.0-SNAPSHOT.jar ^
  --main-class com.example.Main ^
  --dest releases ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-console ^
  --verbose ^
  --runtime-image custom-runtime

pause
