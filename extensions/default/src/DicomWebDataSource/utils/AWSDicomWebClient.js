import { api } from 'dicomweb-client';
import fixMultipart from './fixMultipart';

const { DICOMwebClient } = api;

/**
 * A specialized WADO-RS client for AWS HealthImaging that handles proper frame retrieval
 */
export default class AWSDicomWebClient extends api.DICOMwebClient {
  constructor(options) {
    super(options);
  }

  /**
   * Override frame retrieval to handle AWS HealthImaging specifics
   */
  async retrieveInstanceFrames(options) {
    // AWS HealthImaging specific headers
    const headers = {
      'Accept': 'multipart/related; type=application/octet-stream',
      ...this.headers
    };

    // Ensure frames parameter is properly formatted
    const frame = Array.isArray(options.frameNumbers) ?
      options.frameNumbers[0] :
      parseInt(options.frameNumbers);

    if (isNaN(frame)) {
      throw new Error('Invalid frame number specified');
    }

    // AWS HealthImaging specific URL construction
    const url = this.buildUrl(options.studyInstanceUID, options.seriesInstanceUID, options.sopInstanceUID, frame);

    const response = await this.httpClient.fetch(url, {
      method: 'GET',
      headers
    });

    if (!response.ok) {
      throw new Error('Failed to retrieve frame from AWS HealthImaging');
    }

    const responseContentType = response.headers.get('content-type');

    if (responseContentType && responseContentType.includes('multipart/related')) {
      return fixMultipart(await response.blob());
    }

    return [await response.arrayBuffer()];
  }

  buildUrl(studyUID, seriesUID, instanceUID, frame) {
    return `${this.baseURL}/studies/${studyUID}/series/${seriesUID}/instances/${instanceUID}/frames/${frame}`;
  }
}
