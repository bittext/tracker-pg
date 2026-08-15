import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FitnessHabitStreakBoardDto, FitnessHabitStreakDayDto, FitnessHabitStreakHabitDto } from '../../models/fitness.models';
import { FitnessApiService } from '../../services/fitness-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

@Component({
  selector: 'app-exercise-habit-streak',
  standalone: true,
  imports: [CommonModule, DatePipe, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule],
  templateUrl: './exercise-habit-streak.component.html',
  styleUrl: './exercise-habit-streak.component.scss',
})
export class ExerciseHabitStreakComponent implements OnInit {
  private readonly api = inject(FitnessApiService);
  private readonly snackBar = inject(MatSnackBar);

  board: FitnessHabitStreakBoardDto | null = null;
  loading = false;
  toggling: string | null = null;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.api.habitStreaks().subscribe({
      next: (board) => {
        this.board = board;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load streaks', 'Dismiss', { duration: 5000 });
      },
    });
  }

  toggle(habit: FitnessHabitStreakHabitDto, day: FitnessHabitStreakDayDto): void {
    const key = `${habit.kind}:${day.date}`;
    this.toggling = key;
    this.api.toggleHabitStreak(habit.kind, day.date).subscribe({
      next: (board) => {
        this.board = board;
        this.toggling = null;
      },
      error: (err) => {
        this.toggling = null;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not update day', 'Dismiss', { duration: 5000 });
      },
    });
  }

  dayTip(day: FitnessHabitStreakDayDto): string {
    const status = day.completed ? 'done' : 'not marked';
    return `Day ${day.dayIndex} · ${day.date} · ${status}`;
  }
}
