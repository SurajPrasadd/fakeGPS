# fakeGPS

An Android mock-location app for setting a simulated GPS position on a map or
playing back a driving route at a chosen speed.

## Features

- Choose a location by tapping the map or searching for a place or Indian PIN code.
- Simulate a fixed GPS location.
- Plan a driving route between a start and destination, then simulate movement
  along it at an adjustable speed.
- View route progress and estimated time, with a foreground notification for
  stopping an active simulation.

## Requirements

- Android Studio with Android SDK 37 installed.
- A device or emulator running Android 8.0 (API 26) or later.

## Build and run

1. Open the project in Android Studio and let Gradle sync.
2. Build and install the `app` configuration on a device or emulator.
3. On the device, enable Developer options and set **Select mock location app**
   to **fakeGPS**.
4. Grant the location and notification permissions when prompted.
5. In the app, choose a point on the map or search for a place and tap
   **Set location**. To simulate a route, choose a start and destination, tap
   **Directions**, adjust the speed, and tap **Start**.

Mock locations are an Android developer feature. Other apps may detect or reject
them; use this app for development and testing.

## Map and routing services

The map uses [OpenStreetMap](https://www.openstreetmap.org/) data and tiles.
Place search uses the public [Nominatim](https://nominatim.org/) service, and
driving routes use the public [OSRM demo server](https://project-osrm.org/).
These public services have usage policies and availability limits; review their
terms before use and do not rely on them for production or high-volume traffic.
