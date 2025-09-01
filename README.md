# Minecraft Server UI

This project is a web-based user interface for managing Minecraft servers. It allows users to start, stop, and interact with multiple Minecraft servers through a web browser.

## Features

-   **Server Management**: Start, stop, and restart Minecraft servers.
-   **Real-time Console**: View the server console in real-time and send commands.
-   **Multiple Server Support**: Manage multiple Minecraft server instances.
-   **Web-based Interface**: Access and manage your servers from anywhere with a web browser.
-   **Spring Boot Application**: Built with modern Java technologies for robustness and scalability.

## Technologies Used

-   **Backend**: Java 17, Spring Boot, Spring Data JPA, Spring WebSocket, Spring Security
-   **Frontend**: Thymeleaf, HTML, CSS, JavaScript
-   **Database**: H2 (for development)
-   **Build Tool**: Gradle

## Getting Started

### Prerequisites

-   Java 17 or later
-   Gradle 7.x or later
-   A Minecraft server JAR file (e.g., Paper, Spigot)

### Installation

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/mc-server-ui.git
    cd mc-server-ui
    ```

2.  **Build the project**:
    ```bash
    ./gradlew build
    ```

3.  **Run the application**:
    ```bash
    java -jar build/libs/mc-server-ui-0.0.1-SNAPSHOT.jar
    ```

The application will be available at `http://localhost:8080`.

## Project Structure
