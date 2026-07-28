import defaultMdxComponents from 'fumadocs-ui/mdx';
import { Step, Steps } from 'fumadocs-ui/components/steps';
import type { MDXComponents } from 'mdx/types';
import {
  ApiExample,
  CapabilityGrid,
  DiagramFrame,
  FlowDiagram,
  VerificationBlock,
} from './docs-patterns';
import { OpenAPIPage } from './openapi-page';

export function getMDXComponents(components?: MDXComponents) {
  return {
    ...defaultMdxComponents,
    ApiExample,
    CapabilityGrid,
    DiagramFrame,
    FlowDiagram,
    OpenAPIPage,
    Step,
    Steps,
    VerificationBlock,
    ...components,
  } satisfies MDXComponents;
}

export const useMDXComponents = getMDXComponents;

declare global {
  type MDXProvidedComponents = ReturnType<typeof getMDXComponents>;
}
