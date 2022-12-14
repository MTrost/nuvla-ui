export async function onRequest(context) {
  // Contents of context object
  let {
    request, // same as existing Worker API                           -> needed
    env, // same as existing Worker API                               -> needed for different api backends than nuvla.io
    params, // if filename includes [id] or [[path]]
    waitUntil, // same as ctx.waitUntil in existing Worker API
    next, // used for middleware or to fetch assets
    data, // arbitrary space for passing data between middlewares
  } = context;
  const url = new URL(request.url);

  const { path } = params;
  const [firstPathPart] = path;

  const apiEndpoint = env.API_ENDPOINT || 'https://nuvla.io';

  let response = await fetch(apiEndpoint + url.pathname, request);

  // override base-uri for /api/cloud-entry-point responses
  if (firstPathPart === 'cloud-entry-point') {
    let body = await response.json();
    body = { ...body, 'base-uri': url.origin + '/api/' };
    return new Response(JSON.stringify(body));
  }

  // override all location responses for /api/session
  try {
    let body = await response.json();
    if (body.location) {
      let locationUrl = new URL(body.location);
      locationUrl.host = url.host;
      locationUrl.protocol = url.protocol;
      body = { ...body, location: locationUrl };
    }
    const newResponse = new Response(JSON.stringify(body));
    if (response.headers.has('set-cookie')) {
      newResponse.headers.set('set-cookie', response.headers.get('set-cookie'));
    }
    return newResponse;
  } catch (e) {
    console.error(e);
  }

  return response;
}
