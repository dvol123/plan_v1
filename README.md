# Plan - Android Application

A mobile application for annotating photos with interactive areas (buttons), attaching text comments, photos, and videos.

## Features

- **Project Management**: Create, edit, and delete projects with photos
- **Interactive Areas**: Mark areas on photos using a coordinate grid
- **Media Attachments**: Attach photos and videos to each area
- **State Management**: Assign states with colors to areas
- **Multi-language Support**: Russian, English, and Chinese
- **Import/Export**: ZIP format for device transfer, HTML for PC export
- **Device Sharing**: Wi-Fi Direct and Bluetooth support

## Technical Stack

- **Platform**: Android 14+ (API 34)
- **Language**: Kotlin
- **Architecture**: MVVM with Clean Architecture
- **UI**: Jetpack Compose
- **Database**: Room
- **Dependency Injection**: Hilt
- **Image Loading**: Coil
- **Camera**: CameraX

## Project Structure

```
app/
├── src/main/java/com/plan/app/
│   ├── data/                    # Data layer
│   │   ├── local/               # Local database
│   │   │   ├── dao/             # Data Access Objects
│   │   │   ├── entity/          # Database entities
│   │   │   └── database/        # Room database
│   │   └── repository/          # Repository implementations
│   ├── di/                      # Dependency injection modules
│   ├── domain/                  # Domain layer
│   │   ├── manager/             # State managers
│   │   ├── model/               # Domain models
│   │   ├── repository/          # Repository interfaces
│   │   └── usecase/             # Use cases
│   └── presentation/            # Presentation layer
│       ├── navigation/          # Navigation
│       ├── theme/               # App theme
│       ├── ui/                  # UI screens and components
│       └── viewmodel/           # ViewModels
└── src/main/res/
    ├── values/                  # English strings
    ├── values-ru/               # Russian strings
    └── values-zh/               # Chinese strings
```

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build the project

```bash
./gradlew assembleDebug
```

## Dependencies

All libraries used have open-source licenses (Apache 2.0, MIT):

- Jetpack Compose (Apache 2.0)
- Room (Apache 2.0)
- Hilt (Apache 2.0)
- Coil (Apache 2.0)
- CameraX (Apache 2.0)
- Gson (Apache 2.0)

## Development Stages

1. ✅ Project setup, dependencies, directory structure
2. ✅ Database implementation (entities, DAOs, repositories)
3. ✅ Main screen (project list, CRUD operations)
4. ✅ Project screen (photo display, regions, area card)
5. ✅ Grid editing mode
6. 🔄 Import/Export (ZIP, HTML)
7. 🔄 Wi-Fi Direct/Bluetooth sharing
8. ✅ Localization (3 languages)
9. 🔄 Testing and debugging

## License

This project is proprietary software.

## Author

Developed for the Plan application project.
