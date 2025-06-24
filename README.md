# Arabic OCR Scanner

A simple, clean Android application built with Kotlin and Jetpack Compose to perform Optical Character Recognition (OCR) on Arabic text from images. This project was created with a focus on ease of use, designed specifically for a non-technical user.

## ✨ Features

- **Camera & Gallery:** Capture a new image or select an existing one from the device's gallery.
- **High-Accuracy OCR:** Utilizes the powerful Google Cloud Vision API to ensure high-quality text recognition for Arabic script.
- **Clean UI:** A minimalist interface that makes the app straightforward and intuitive to navigate.
- **Share Functionality:** Extracted text is saved into a `.docx` (Microsoft Word) file, which can be easily shared to other apps like WhatsApp, Google Drive, or Email using Android's native share feature.
- **Arabic First:** The user interface is translated and defaults to Arabic.

## 🛠️ Core Technologies

- **Kotlin** & **Jetpack Compose** for the native Android UI.
- **Google Cloud Vision API** for server-side OCR.
- **CameraX** for the camera preview and capture functionality.
- **Apache POI** for creating `.docx` files.

## 🚀 Building the App

To build and run this project yourself, you will need to:

1.  **Get a Google Cloud API Key:**

    - Enable the Cloud Vision API in your Google Cloud Platform project.
    - Make sure billing is enabled (a free tier is available).
    - Create an API key.

2.  **Add the API Key:**

    - Create a file named `local.properties` in the root directory of the project.
    - Add your API key to this file like so:
      ```properties
      google.api.key="YOUR_API_KEY_HERE"
      ```

3.  **Generate a Signing Key for Release Builds:**
    - Run the `keytool` command to create a `.keystore` file.
    - Create a `keystore.properties` file in the root directory with your key's credentials (`storePassword`, `keyPassword`, `keyAlias`, `storeFile`).
    - The build is already configured to read from this file for release builds.

## ✍️ Author

Made by **Donsmak**.
