export interface PortalSection {
  path: string;
  label: string;
  heading: string;
  description: string;
}

export interface PortalTab {
  path: string;
  label: string;
}

export interface AdminArea {
  section: PortalSection;
  tabs: readonly PortalTab[];
}
