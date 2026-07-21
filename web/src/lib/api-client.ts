import { client } from "@/lib/hey-api/client.gen"

// REST calls stay same-origin. In development Vite proxies /api to Spring;
// in production the reverse proxy serves both surfaces from one origin.
client.setConfig({
  baseUrl: window.location.origin,
  credentials: "same-origin",
})

export { client }
