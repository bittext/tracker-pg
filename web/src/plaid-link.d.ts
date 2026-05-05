/** Global from https://cdn.plaid.com/link/v2/stable/link-initialize.js (Plaid Link). */
declare namespace PlaidLink {
  interface OnSuccessMetadata {
    institution?: { name: string; institution_id: string };
    accounts?: Array<{ id: string; name: string }>;
    link_session_id?: string;
  }
}

declare global {
  interface Window {
    Plaid?: {
      create: (config: {
        token: string;
        onSuccess: (publicToken: string, metadata: PlaidLink.OnSuccessMetadata) => void;
        onExit?: (error: unknown, metadata: unknown) => void;
      }) => { open: () => void; destroy: () => void };
    };
  }
}

export {};
