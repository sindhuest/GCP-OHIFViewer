window.config = {
  routerBasename: '/',
  showStudyList: true,

  defaultDataSourceName: 'dicomweb',

  dataSources: [
    {
      namespace: '@ohif/extension-default.dataSourcesModule.dicomweb',
      sourceName: 'dicomweb',
      configuration: {
        friendlyName: 'GCP DICOM Proxy',
        name: 'dicomweb',

        // These must be EXACTLY these URLs - no v1/projects stuff
        wadoUriRoot: 'http://localhost:8080/dicomweb',
        qidoRoot: 'http://localhost:8080/dicomweb',
        wadoRoot: 'http://localhost:8080/dicomweb',

        // Critical: Disable features that might cause URL construction issues
        qidoSupportsIncludeField: false,
        supportsFuzzyMatching: false,
        supportsWildcard: false,
        staticWado: true,
        loadAllInstances: true,
        preloadAllInstances: true,
        metadataProvider: {
          enableInstanceLoading: true,
          preloadInstances: true
        },
        singlepart: 'bulkdata',
        supportsReject: false,
        supportsInstanceMetadata: true,
        enableStudyLazyLoad: true,
        omitQuotationForMultipartRequest: true,

        // Force simple content types to avoid parsing issues
        acceptHeader: 'application/dicom+json',
        requestTransferSyntaxUID: false,

        imageRendering: 'wadors',
        thumbnailRendering: 'wadors',

        // Essential for bulkdata/image loading
        bulkDataURI: {
          enabled: true,
          relativeResolution: 'studies',
        },

        requestOptions: {
          useInstanceMetadata: true,
          requestFromBrowser: true,
          headers: {
            'Accept': 'application/dicom+json',
          },
        },
      },
    },
  ],

  extensions: [
    '@ohif/extension-default',
    '@ohif/extension-cornerstone',
  ],

  modes: [
    '@ohif/mode-longitudinal',
  ],

  defaultMode: '@ohif/mode-longitudinal',
};
