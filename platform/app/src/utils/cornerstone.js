import { init as csTools3dInit } from '@cornerstonejs/tools';
import { init as csVolumeInit } from '@cornerstonejs/core';
import { init as initVolumeLoader } from '@cornerstonejs/streaming-image-volume-loader';
import dicomImageLoader from '@cornerstonejs/dicom-image-loader';

export async function initCornerstoneServices() {
    await csVolumeInit();
    await csTools3dInit();
    await initVolumeLoader();

    // Setup dicom image loader
    dicomImageLoader.configure({
        beforeSend: (xhr) => {
            xhr.setRequestHeader('Accept', ['application/dicom', 'application/octet-stream'].join(','));
        },
        useWebWorkers: true,
        decodeConfig: {
            convertFloatPixelDataToInt: false,
        },
    });

    // Handle image loading errors properly
    const imageLoadFailedHandler = (error) => {
        console.error('Image load failed:', error);
    };

    document.addEventListener('cornerstoneimageloadfailed', imageLoadFailedHandler);

    return () => {
        document.removeEventListener('cornerstoneimageloadfailed', imageLoadFailedHandler);
    };
}
