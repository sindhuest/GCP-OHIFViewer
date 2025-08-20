// AWS HealthImaging configuration through backend proxy
export default {
  // Standard DICOM web configuration pointing to backend proxy
  wadoRoot: '/api/aws/dicom-web',  // Your backend proxy endpoint
  qidoRoot: '/api/aws/dicom-web',
  wadoUri: '/api/aws/wado',

  // Standard headers and settings
  qidoSupportsIncludeField: true,
  imageRendering: 'wadors',
  thumbnailRendering: 'wadors',
  omitQuotationForMultipartRequest: true,

  // Use standard DICOM web client since AWS specifics are handled by backend
  supportsNativeDICOMModel: true,
  enableStudyLazyLoad: true
};
