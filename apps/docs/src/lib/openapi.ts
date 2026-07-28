import publicOpenAPI from '../../generated/openapi.public.json';
import { createOpenAPI } from 'fumadocs-openapi/server';
import type { OpenAPIV3_2 } from 'fumadocs-openapi';

export const publicOpenAPISchemaId = 'orgmemory-public';

export const openapi = createOpenAPI({
  input: {
    [publicOpenAPISchemaId]: publicOpenAPI as OpenAPIV3_2.Document,
  },
});
