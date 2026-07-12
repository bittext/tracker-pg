import { Component } from '@angular/core';
import { RobinhoodTradingPanelComponent } from '../../finance/robinhood-trading-panel/robinhood-trading-panel.component';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';

@Component({
  selector: 'app-markets-execution',
  standalone: true,
  imports: [PageHeaderComponent, RobinhoodTradingPanelComponent],
  template: `
    <app-page-header
      title="Execution"
      subtitle="Robinhood trading — import CSV, review positions, and place trades when configured."
    />
    <app-robinhood-trading-panel />
  `,
})
export class MarketsExecutionComponent {}
