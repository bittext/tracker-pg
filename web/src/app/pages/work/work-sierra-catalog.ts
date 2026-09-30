export interface SierraModule {
  readonly title: string;
  readonly path: string;
  readonly assetPath: string;
}

export interface SierraSection {
  readonly label: string;
  readonly modules: readonly SierraModule[];
}

function mod(title: string, path: string): SierraModule {
  return { title, path, assetPath: `/work-projects/sierra/${path}` };
}

export const SIERRA_CATALOG: readonly SierraSection[] = [
  {
    label: 'Start',
    modules: [mod('Catalog', 'index.md'), mod('Sources', 'sources.md')],
  },
  {
    label: 'Product',
    modules: [
      mod('Overview', 'product/overview.md'),
      mod('Pricing and outcomes', 'product/pricing-and-outcomes.md'),
      mod('Channels', 'product/channels.md'),
    ],
  },
  {
    label: 'Architecture',
    modules: [
      mod('Agent OS', 'architecture/agent-os.md'),
      mod('Agent Data Platform', 'architecture/agent-data-platform.md'),
      mod('Models and constellation', 'architecture/models-and-constellation.md'),
    ],
  },
  {
    label: 'Agents',
    modules: [
      mod('Journeys and skills', 'agents/journeys-and-skills.md'),
      mod('Agent SDK', 'agents/agent-sdk.md'),
      mod('Ghostwriter', 'agents/ghostwriter.md'),
    ],
  },
  {
    label: 'Studio',
    modules: [
      mod('Agent Studio', 'studio/agent-studio.md'),
      mod('Knowledge', 'studio/knowledge.md'),
      mod('Branding and controls', 'studio/branding-and-controls.md'),
    ],
  },
  {
    label: 'Integrations',
    modules: [
      mod('Systems and actions', 'integrations/systems-and-actions.md'),
      mod('Contact center and handoff', 'integrations/contact-center-and-handoff.md'),
    ],
  },
  {
    label: 'Operations',
    modules: [
      mod('Insights and monitoring', 'operations/insights-and-monitoring.md'),
      mod('Testing and release', 'operations/testing-and-release.md'),
      mod('Trust and compliance', 'operations/trust-and-compliance.md'),
    ],
  },
  {
    label: 'Journal',
    modules: [
      mod('How to journal', 'journal/README.md'),
      mod('2026-09-29 library started', 'journal/2026-09-29-library-started.md'),
    ],
  },
];
