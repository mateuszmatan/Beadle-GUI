import { SvgIconRegistryService } from 'angular-svg-icon';
import { addPortalIcons } from './icons';

export const GRID_THEME = 'ag-theme-balham';

export function registerIcons(registry: SvgIconRegistryService): void {
  addPortalIcons(registry);
}
