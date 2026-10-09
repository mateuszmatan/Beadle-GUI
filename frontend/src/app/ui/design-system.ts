import { v6RegisterIcons } from '@v6/v6-icons';
import { SvgIconRegistryService } from 'angular-svg-icon';
import { addPortalIcons } from './icons';

export const GRID_THEME = 'ag-theme-balham v6-table-theme--bbh';

export function registerIcons(registry: SvgIconRegistryService): void {
  v6RegisterIcons(registry);
  addPortalIcons(registry);
}
