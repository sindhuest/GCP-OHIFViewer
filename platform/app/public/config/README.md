# GCP OHIF Viewer Frontend

This project contains the **frontend configuration of OHIF Viewer** integrated with **Google Cloud Healthcare API (DICOMWeb)** through a Spring Boot backend proxy.
The frontend is based on the [OHIF Viewer](https://github.com/OHIF/Viewers) and has been customized to connect with the backend proxy service that securely handles authentication and communication with GCP APIs.

---


---

## ⚙️ Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/sindhuest/GCP-OHIFViewer.git
cd GCP-OHIFViewer


### 2. Install Dependencies

yarn install


### 3. Configure OHIF
 📂 Project Structure

OHIF-gcpViewer-frontend/
│
├── platform/
│   └── viewer/
│       ├── public/
│       │   ├── app-config.js        # Customized OHIF configuration for GCP proxy
│       │   └── index.html
│       │
│       ├── src/
│       │   ├── config/
│       │   │   ├── default.js       # Modified to point to GCP DICOM proxy backend
│       │   │   └── extensions.js
│       │   ├── routes/
│       │   ├── components/
│       │   └── index.js
│       │
│       ├── package.json
│       └── yarn.lock
│
├── .gitignore
├── README.md   # Your frontend README
└── ...


Key modified files

Viewers\platform\app\public\config\app-config.js
Defines DICOMWeb server settings.
Updated to point to your backend proxy (http://localhost:8080/dicomweb) instead of directly hitting GCP.

Viewers\platform\app\public\config\default.js
OHIF Viewer default configuration.
Updated to load proxy-based DICOMWeb endpoints (QIDO, WADO, STOW).

This ensures all QIDO (query), WADO (retrieve), and metadata requests are routed via the Spring Boot backend proxy, which securely communicates with GCP.

4. Run Development Server
       yarn start


The app will be available at:
👉 http://localhost:3000

5. Build for Production
       yarn build

🔗 Proxy Backend

This frontend depends on the Spring Boot proxy backend available here:
👉 OHIF-gcpViewer (Backend)

The backend:

Signs requests for Google Cloud Healthcare API (DICOMWeb: QIDO, WADO, STOW)
Handles authentication via service account tokens
Forwards responses to OHIF Viewer in the expected format

📖 References

Google Cloud Healthcare API Documentation
https://cloud.google.com/healthcare/docs/dicom

OHIF Viewer Documentation
https://docs.ohif.org/
