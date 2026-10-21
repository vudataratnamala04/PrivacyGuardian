# Privacy Guardian — Java + React + MySQL

## Stack

- Frontend: React + Vite
- Backend: Java 21 + Spring Boot
- Database: MySQL
- Authentication: JWT + BCrypt
- OCR: Tesseract (external executable)
- QR/Barcode: ZXing
- PDF: Apache PDFBox
- DOCX: Apache POI
- Integrity: SHA-256

**Python is NOT used by the backend.** The application backend is Java/Spring Boot. Tesseract is an external OCR program; it is not Python.

## Database

Create the MySQL database once:

```sql
CREATE DATABASE privacy_guardian;
```

The application uses JPA/Hibernate to create/update the tables.

## Run backend

Open CMD in `backend`:

```bat
mvnd clean install
mvnd spring-boot:run
```

Backend:
`http://localhost:8080`

Database defaults:

- host: `127.0.0.1`
- port: `3306`
- database: `privacy_guardian`
- username: `root`
- password: `Ratna@2007`

You can override the password with:

```bat
set DB_PASSWORD=your_password
```

## Tesseract

Install Tesseract OCR for Windows. The backend checks:

```text
C:\Program Files\Tesseract-OCR\tesseract.exe
C:\Program Files (x86)\Tesseract-OCR\tesseract.exe
```

or uses `tesseract` from PATH.

## Run frontend

Open a second CMD in `frontend`:

```bat
npm install
npm run dev
```

Open the Vite URL, normally `http://localhost:5173`.

## Demo accounts

```text
USER
username: user
password: user123

ADMIN
username: admin
password: admin123
```

## Actual protected-image flow

```text
Upload
  ↓
OCR / detection
  ↓
Risk score
  ↓
Agentic decision
  ↓
Backup original
  ↓
Actual region redaction
  ↓
Protected image written to disk
  ↓
Image is reopened and dimension-checked
  ↓
Download Protected
```

The image protection code deliberately does **not** union all OCR boxes into one giant rectangle. Each detection box is protected separately, and near-full-image boxes are rejected. This prevents the previous whole-image-black failure.

Supported image methods include:

- BLACKOUT
- WHITEOUT
- BLUR
- PIXELATE
- MOSAIC
- REPLACE / LABEL

For PNG, alpha is preserved. JPEG output is safely flattened to RGB.

## Important

The protected download endpoint returns the actual generated file bytes. The UI status is not the protection itself.

Before project submission, test with a real image containing fake PII and open the downloaded protected image to verify that:
1. the file opens;
2. the non-sensitive image remains visible;
3. sensitive regions are actually changed;
4. the original is still preserved in backups;
5. SHA-256 verification works.


## Included redaction test

Use:

```text
test-data/TEST_INPUT_FAKE_PII.png
```

This contains only fake data.

The expected visual result is:

```text
test-data/EXPECTED_PROTECTED_SAMPLE.png
```

The protected image should keep the heading, PHOTO area, and non-sensitive text visible while masking only the fake PII values. The application itself should be tested by uploading the input image, letting the agent protect it, downloading the protected file, and opening that downloaded file.


## Final protection guarantees
- Face detection and face blur are enabled with OpenCV.
- Sensitive OCR regions are protected independently; OCR boxes are never merged into a whole-image rectangle.
- Protected images are reopened after writing and their dimensions/file size are verified.
- Protected downloads return the actual protected file bytes with the correct media type.
- Original files and backups remain separate.
- MySQL remains the database; Java/Spring Boot is the backend. Python is not used as the backend.


## Login Fix (2026-10-07)
- Login now accepts either the registered username or registered email address.
- Authentication uses the entity's `passwordHash` BCrypt field.
- Added a repository lookup for username-or-email login.
- Frontend login field now says `Username or Email`.
- Existing account data is not deleted or recreated.


## Session / 403 fix
This build detects stale JWT sessions when an authenticated dashboard request returns 401/403, clears the saved browser session, and prompts the user to log in again. Spring Security returns 401 for unauthenticated requests and 403 for role-denied requests. See `FIXES-AND-RUN.md` for run steps and validation checklist.
