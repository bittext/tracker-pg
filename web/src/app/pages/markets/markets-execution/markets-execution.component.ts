import { Component } from '@angular/core';
import { RobinhoodTradingPanelComponent } from '../../finance/robinhood-trading-panel/robinhood-trading-panel.component';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';

@Component({
  selector: 'app-markets-execution',
  standalone: true,
  imports: [PageHeaderComponent, RobinhoodTradingPanelComponent],
  template: `
    <app-page-header
      title="Trade"
      subtitle="Live desk for Agentic ••••3550 — review, place, and capture. Analytics stays the record."
    />
    <app-robinhood-trading-panel />
  `,
})
export class MarketsExecutionComponent {}
