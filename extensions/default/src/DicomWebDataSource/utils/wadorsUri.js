function buildInstanceWadoRsUri(instance, config) {
  const { StudyInstanceUID, SeriesInstanceUID, SOPInstanceUID } = instance;
  return `${config.wadoRoot}/studies/${StudyInstanceUID}/series/${SeriesInstanceUID}/instances/${SOPInstanceUID}`;
}

function buildInstanceFrameWadoRsUri(instance, config, frame) {
  const baseWadoRsUri = buildInstanceWadoRsUri(instance, config);

  // AWS HealthImaging expects 1-based frame numbers
  frame = frame || 1;

  // AWS HealthImaging specific headers will be added by the client
  return `${baseWadoRsUri}/frames/${frame}`;
}

function getWADORSImageId(instance, config, frame) {
  const wadorsuri = buildInstanceFrameWadoRsUri(instance, config, frame);

  if (!wadorsuri) {
    return;
  }

  return `wadors:${wadorsuri}`;
}

export { getWADORSImageId, buildInstanceWadoRsUri, buildInstanceFrameWadoRsUri };
