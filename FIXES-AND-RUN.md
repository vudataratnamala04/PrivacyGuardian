# Privacy Guardian — Fixed Session / Recovery Build

This is the existing Java + Spring Boot + MySQL + React/Vite project with a small session-handling fix.

## Run it on Windows

1. Start MySQL and ensure database `privacy_guardian` exists.
2. In one CMD window, from this project folder, run `start-backend.bat`.
3. In a second CMD window, run `start-frontend.bat`.
4. Open the Vite URL shown in the frontend terminal, normally `http://localhost:5173`.

You can also run the commands manually:

Backend:
```bat
cd backend
mvnd spring-boot:run
```

Frontend:
```bat
cd frontend
npm install
npm run dev
```

## Fix included

- The frontend now detects an expired/invalid token when `/api/dashboard` returns 401/403, clears the stale local session, and returns to the login screen.
- The scan/protect actions also clear a stale session if the API rejects the token.
- Spring Security now returns HTTP 401 for missing/invalid authentication and keeps HTTP 403 for authenticated users who do not have the required role (such as a USER accessing the ADMIN area).
- Recovery download object URLs are revoked after a short delay so the browser has time to start the download.

This is particularly relevant if different Privacy Guardian project versions have been opened on the same `localhost:5173`: browser `localStorage` is shared by origin, and an old JWT from a different version/secret can otherwise cause API requests to fail.

## Existing features retained

- Sign up, login, logout, JWT and BCrypt password hashing
- USER and ADMIN roles / Admin SOC
- Upload and scan of supported image/PDF/DOCX/TXT/CSV files
- OCR and sensitive-data patterns, QR/barcode and face detection
- Risk score and agent decision
- Actual protected-file generation and protected-file download
- Original backup, restore/recovery download and SHA-256 integrity verification
- My Files, dashboard and audit logs

## Demo accounts

- USER: `user` / `user123`
- ADMIN: `admin` / `admin123`

Use a newly logged-in session after extracting this build. If a previous project version left an old session in the browser, this build should detect it and return to login.

## Important environment requirements

- Java compatible with the backend's configured Java release (the project POM targets Java 21; use a compatible JDK for the most predictable build).
- MySQL service running, database `privacy_guardian`, and credentials matching `backend/src/main/resources/application.properties` or environment variables.
- Node.js/npm for the React frontend.
- Tesseract OCR installed for OCR functionality. Face detection uses the included OpenCV dependency/cascade resource.

## Before submission

Run through login, upload/scan a dummy file, open the generated protected download, restore the backup, download the recovered original, and verify integrity. A successful build does not itself prove that every file type and redaction case is perfect.
