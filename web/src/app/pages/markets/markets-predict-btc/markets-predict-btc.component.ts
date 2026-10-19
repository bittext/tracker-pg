import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Btc15DeskDto } from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-markets-predict-btc',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatProgressBarModule, MatSnackBarModule, RouterLink],
  templateUrl: './markets-predict-btc.component.html',
  styleUrl: './markets-predict-btc.component.scss',
})
export class MarketsPredictBtcComponent implements OnInit {
  private readonly api = inject(RhPredictApiService);
  private readonly snack = inject(MatSnackBar);

  loading = false;
  desk: Btc15DeskDto | null = null;
  date = todayCentral();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.btc15(this.date).subscribe({
      next: (desk) => {
        this.desk = desk;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snack.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }
}

function todayCentral(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}
