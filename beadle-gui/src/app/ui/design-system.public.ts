import { SvgIconRegistryService } from 'angular-svg-icon';
import { addAppIcons } from './icons';

export const GRID_THEME = 'ag-theme-balham';

export function registerIcons(registry: SvgIconRegistryService): void {
  addAppIcons(registry);
}
