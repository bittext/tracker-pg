import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  inject,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import maplibregl, { GeoJSONSource, MapLayerMouseEvent } from 'maplibre-gl';
import type { FeatureCollection } from 'geojson';
import {
  TravelGeocodeResultDto,
  TravelPlaceDto,
  TravelPlaceMapDto,
  TravelPlaceStatus,
  TravelTripDetailDto,
  TravelTripStatus,
  TravelTripSummaryDto,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { SafeMarkdownPipe } from '../../../pipes/safe-markdown.pipe';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { environment } from '../../../../environments/environment';

type TravelLens = 'day' | 'month' | 'year' | 'overview';

@Component({
  selector: 'app-management-travel-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    DragDropModule,
    SafeMarkdownPipe,
  ],
  templateUrl: './management-travel-panel.component.html',
  styleUrl: './management-travel-panel.component.scss',
})
export class ManagementTravelPanelComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly mapHost = viewChild.required<ElementRef<HTMLDivElement>>('mapHost');

  private map: maplibregl.Map | null = null;
  private mapReady = false;
  private resizeObserver: ResizeObserver | null = null;

  trips: TravelTripSummaryDto[] = [];
  tripDetail: TravelTripDetailDto | null = null;
  mapPlaces: TravelPlaceMapDto[] = [];
  loadingTrips = false;
  loadingMap = false;
  photoUploading = false;
  /** Saving place order after drag-and-drop. */
  reorderPlacesBusy = false;
  geocodeBusy = false;
  /** Shown after a successful address search for the “Add place” form. */
  newPlaceGeocodeHint = '';
  /** Shown after a successful address search in the place editor. */
  editPlaceGeocodeHint = '';

  lens: TravelLens = 'overview';
  lensDayIso = this.toIsoDate(new Date());
  lensMonth = new Date().getMonth() + 1;
  lensYear = new Date().getFullYear();

  selectedTripId: number | null = null;
  selectedPlaceId: number | null = null;

  newTrip = {
    title: '',
    summary: '',
    startDate: this.toIsoDate(new Date()),
    endDate: null as string | null,
    status: 'PLANNING' as TravelTripStatus,
    colorHex: '#6366f1',
  };

  newPlace = {
    name: '',
    latitude: 0,
    longitude: 0,
    address: '',
    placeStatus: 'PLANNED' as TravelPlaceStatus,
    visitDate: null as string | null,
    notes: '',
    sortOrder: 0,
  };

  readonly tripStatuses: TravelTripStatus[] = ['PLANNING', 'ACTIVE', 'COMPLETED'];
  readonly placeStatuses: TravelPlaceStatus[] = ['PLANNED', 'VISITED'];
  readonly monthOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;

  ngOnInit(): void {
    this.refreshAll();
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.initMapWhenReady());
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    this.destroyMap();
  }

  refreshAll(): void {
    this.reloadTrips();
    this.reloadMapPlaces();
  }

  reloadTrips(): void {
    this.loadingTrips = true;
    this.api.listTravelTrips().subscribe({
      next: (rows) => {
        this.trips = rows;
        this.loadingTrips = false;
        if (this.selectedTripId != null && !rows.some((t) => t.id === this.selectedTripId)) {
          this.selectedTripId = null;
          this.tripDetail = null;
        }
      },
      error: (e) => {
        this.loadingTrips = false;
        this.err('Could not load trips', e);
      },
    });
  }

  selectTrip(id: number): void {
    this.selectedTripId = id;
    this.selectedPlaceId = null;
    this.newPlaceGeocodeHint = '';
    this.editPlaceGeocodeHint = '';
    this.api.getTravelTrip(id).subscribe({
      next: (d) => {
        this.tripDetail = d;
      },
      error: (e) => this.err('Could not load trip', e),
    });
  }

  clearTripSelection(): void {
    this.selectedTripId = null;
    this.tripDetail = null;
    this.selectedPlaceId = null;
    this.newPlaceGeocodeHint = '';
    this.editPlaceGeocodeHint = '';
  }

  setLens(v: TravelLens): void {
    this.lens = v;
    this.reloadMapPlaces();
  }

  reloadMapPlaces(): void {
    const r = this.rangeForLens();
    this.loadingMap = true;
    this.api.travelPlacesForMap(r?.from ?? undefined, r?.to ?? undefined).subscribe({
      next: (rows) => {
        this.mapPlaces = rows;
        this.loadingMap = false;
        this.updateMapGeoJson();
      },
      error: (e) => {
        this.mapPlaces = [];
        this.loadingMap = false;
        this.err('Could not load map places', e);
      },
    });
  }

  saveNewTrip(): void {
    const title = (this.newTrip.title || '').trim();
    if (!title) {
      this.snackBar.open('Enter a trip title', 'Dismiss', { duration: 4000 });
      return;
    }
    this.api
      .createTravelTrip({
        title,
        summary: this.newTrip.summary || '',
        startDate: this.newTrip.startDate,
        endDate: this.newTrip.endDate || null,
        status: this.newTrip.status,
        colorHex: this.newTrip.colorHex || null,
      })
      .subscribe({
        next: (d) => {
          this.snackBar.open('Trip created', undefined, { duration: 2500 });
          this.newTrip = {
            title: '',
            summary: '',
            startDate: this.toIsoDate(new Date()),
            endDate: null,
            status: 'PLANNING',
            colorHex: '#6366f1',
          };
          this.reloadTrips();
          this.reloadMapPlaces();
          this.selectTrip(d.id);
        },
        error: (e) => this.err('Could not create trip', e),
      });
  }

  saveTripMeta(): void {
    if (!this.tripDetail) {
      return;
    }
    const t = this.tripDetail;
    this.api
      .updateTravelTrip(t.id, {
        title: t.title,
        summary: t.summary ?? '',
        startDate: t.startDate,
        endDate: t.endDate ?? null,
        status: t.status,
        colorHex: t.colorHex ?? null,
      })
      .subscribe({
        next: (d) => {
          this.tripDetail = d;
          this.snackBar.open('Trip saved', undefined, { duration: 2000 });
          this.reloadTrips();
          this.reloadMapPlaces();
        },
        error: (e) => this.err('Could not save trip', e),
      });
  }

  deleteTrip(): void {
    if (!this.tripDetail) {
      return;
    }
    if (!confirm(`Delete trip “${this.tripDetail.title}” and all its places and photos?`)) {
      return;
    }
    const id = this.tripDetail.id;
    this.api.deleteTravelTrip(id).subscribe({
      next: () => {
        this.snackBar.open('Trip deleted', undefined, { duration: 2500 });
        this.clearTripSelection();
        this.reloadTrips();
        this.reloadMapPlaces();
      },
      error: (e) => this.err('Could not delete trip', e),
    });
  }

  addPlace(): void {
    if (!this.tripDetail) {
      return;
    }
    const name = (this.newPlace.name || '').trim();
    if (!name) {
      this.snackBar.open('Enter a place name', 'Dismiss', { duration: 4000 });
      return;
    }
    this.api
      .addTravelPlace(this.tripDetail.id, {
        name,
        latitude: this.newPlace.latitude,
        longitude: this.newPlace.longitude,
        address: this.newPlace.address || null,
        placeStatus: this.newPlace.placeStatus,
        visitDate: this.newPlace.visitDate || null,
        notes: this.newPlace.notes || '',
        sortOrder: this.newPlace.sortOrder,
      })
      .subscribe({
        next: (d) => {
          this.tripDetail = d;
          this.snackBar.open('Place added', undefined, { duration: 2000 });
          this.newPlace = {
            name: '',
            latitude: this.newPlace.latitude,
            longitude: this.newPlace.longitude,
            address: '',
            placeStatus: 'PLANNED',
            visitDate: null,
            notes: '',
            sortOrder: (d.places?.length ?? 0),
          };
          this.reloadTrips();
          this.reloadMapPlaces();
        },
        error: (e) => this.err('Could not add place', e),
      });
  }

  onPlacesReorderDrop(event: CdkDragDrop<void>): void {
    if (!this.tripDetail || event.previousIndex === event.currentIndex) {
      return;
    }
    const tripId = this.tripDetail.id;
    const places = [...this.tripDetail.places];
    moveItemInArray(places, event.previousIndex, event.currentIndex);
    const orderedIds = places.map((p) => p.id);
    this.tripDetail = { ...this.tripDetail, places };
    this.reorderPlacesBusy = true;
    this.api.reorderTravelPlaces(tripId, orderedIds).subscribe({
      next: (d) => {
        this.reorderPlacesBusy = false;
        this.tripDetail = d;
        this.snackBar.open('Place order saved', undefined, { duration: 2000 });
        this.reloadTrips();
        this.reloadMapPlaces();
      },
      error: (e) => {
        this.reorderPlacesBusy = false;
        this.err('Could not save place order', e);
        this.selectTrip(tripId);
      },
    });
  }

  savePlace(p: TravelPlaceDto): void {
    this.api
      .updateTravelPlace(p.id, {
        name: p.name,
        latitude: p.latitude,
        longitude: p.longitude,
        address: p.address,
        placeStatus: p.placeStatus,
        visitDate: p.visitDate,
        notes: p.notes ?? '',
        sortOrder: p.sortOrder,
      })
      .subscribe({
        next: (d) => {
          this.tripDetail = d;
          this.snackBar.open('Place saved', undefined, { duration: 2000 });
          this.reloadTrips();
          this.reloadMapPlaces();
        },
        error: (e) => this.err('Could not save place', e),
      });
  }

  deletePlace(p: TravelPlaceDto): void {
    if (!confirm(`Remove “${p.name}”?`)) {
      return;
    }
    this.api.deleteTravelPlace(p.id).subscribe({
      next: () => {
        this.snackBar.open('Place removed', undefined, { duration: 2000 });
        if (this.selectedPlaceId === p.id) {
          this.selectedPlaceId = null;
        }
        if (this.tripDetail) {
          this.selectTrip(this.tripDetail.id);
        }
        this.reloadTrips();
        this.reloadMapPlaces();
      },
      error: (e) => this.err('Could not delete place', e),
    });
  }

  onPlacePhotoSelected(event: Event, placeId: number): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) {
      return;
    }
    this.photoUploading = true;
    const file = files[0];
    this.api.uploadTravelPlacePhoto(placeId, file).subscribe({
      next: () => {
        this.photoUploading = false;
        input.value = '';
        if (this.tripDetail) {
          this.selectTrip(this.tripDetail.id);
        }
        this.reloadMapPlaces();
        this.snackBar.open('Photo uploaded', undefined, { duration: 2000 });
      },
      error: (e) => {
        this.photoUploading = false;
        input.value = '';
        this.err('Upload failed', e);
      },
    });
  }

  removePlacePhoto(photoId: number): void {
    this.api.deleteTravelPlacePhoto(photoId).subscribe({
      next: () => {
        if (this.tripDetail) {
          this.selectTrip(this.tripDetail.id);
        }
        this.snackBar.open('Photo removed', undefined, { duration: 2000 });
      },
      error: (e) => this.err('Could not remove photo', e),
    });
  }

  openPlacePhoto(photoId: number): void {
    this.api.getTravelPlacePhotoBlob(photoId, 'inline').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const w = window.open(url, '_blank', 'noopener');
        if (!w) {
          URL.revokeObjectURL(url);
        } else {
          w.addEventListener('beforeunload', () => URL.revokeObjectURL(url));
        }
      },
      error: (e) => this.err('Could not open photo', e),
    });
  }

  useMapCenterForNewPlace(): void {
    const c = this.map?.getCenter();
    if (!c) {
      return;
    }
    this.newPlace.longitude = Math.round(c.lng * 1e6) / 1e6;
    this.newPlace.latitude = Math.round(c.lat * 1e6) / 1e6;
    this.newPlaceGeocodeHint = '';
  }

  lookUpNewPlaceAddress(): void {
    const q = (this.newPlace.address || '').trim();
    if (!q) {
      this.snackBar.open('Enter an address, city, or place name to look up.', undefined, { duration: 3500 });
      return;
    }
    this.runGeocode(q, (r) => {
      this.newPlace.latitude = this.roundCoord(r.latitude);
      this.newPlace.longitude = this.roundCoord(r.longitude);
      this.newPlace.address = r.displayName || this.newPlace.address;
      if (!(this.newPlace.name || '').trim()) {
        const guess = (r.locality || '').trim() || (r.displayName || '').split(',')[0]?.trim() || '';
        if (guess) {
          this.newPlace.name = guess;
        }
      }
      this.newPlaceGeocodeHint = this.formatGeocodeSummary(r);
      this.flyMapTo(r.longitude, r.latitude, 12);
    });
  }

  lookUpPlaceAddress(p: TravelPlaceDto): void {
    const q = (p.address || '').trim() || (p.name || '').trim();
    if (!q) {
      this.snackBar.open('Enter an address in the field above, or a name to search.', undefined, { duration: 3500 });
      return;
    }
    this.runGeocode(q, (r) => {
      p.latitude = this.roundCoord(r.latitude);
      p.longitude = this.roundCoord(r.longitude);
      p.address = r.displayName || p.address;
      this.editPlaceGeocodeHint = this.formatGeocodeSummary(r);
      this.flyMapTo(r.longitude, r.latitude, 12);
    });
  }

  private runGeocode(q: string, onOk: (r: TravelGeocodeResultDto) => void): void {
    this.geocodeBusy = true;
    this.api.travelGeocode(q).subscribe({
      next: (r) => {
        this.geocodeBusy = false;
        onOk(r);
        this.snackBar.open('Location found on map', undefined, { duration: 2200 });
      },
      error: (e) => {
        this.geocodeBusy = false;
        this.err('Address lookup failed', e);
      },
    });
  }

  private formatGeocodeSummary(r: TravelGeocodeResultDto): string {
    const parts = [r.locality, r.region, r.country].filter((x) => (x || '').trim().length > 0);
    return parts.length > 0 ? parts.join(' · ') : (r.displayName || 'Matched location');
  }

  private roundCoord(n: number): number {
    return Math.round(n * 1e6) / 1e6;
  }

  private flyMapTo(lng: number, lat: number, zoom: number): void {
    if (!this.map) {
      return;
    }
    this.map.flyTo({
      center: [lng, lat],
      zoom: Math.max(this.map.getZoom(), zoom),
      essential: true,
    });
  }

  selectPlaceFromList(id: number): void {
    this.selectedPlaceId = id;
    this.editPlaceGeocodeHint = '';
    const p = this.mapPlaces.find((x) => x.id === id);
    if (p && this.map) {
      this.map.flyTo({ center: [p.longitude, p.latitude], zoom: Math.max(this.map.getZoom(), 10), essential: true });
    }
    this.setSelectionHaloFilter();
  }

  monthName(m: number): string {
    return new Date(2000, m - 1, 1).toLocaleString(undefined, { month: 'long' });
  }

  selectedPlace(): TravelPlaceDto | null {
    if (!this.tripDetail || this.selectedPlaceId == null) {
      return null;
    }
    return this.tripDetail.places.find((p) => p.id === this.selectedPlaceId) ?? null;
  }

  private rangeForLens(): { from: string; to: string } | null {
    if (this.lens === 'overview') {
      return null;
    }
    if (this.lens === 'day') {
      return { from: this.lensDayIso, to: this.lensDayIso };
    }
    const y = Math.trunc(Number(this.lensYear)) || new Date().getFullYear();
    const mo = Math.min(12, Math.max(1, Math.trunc(Number(this.lensMonth)) || 1));
    if (this.lens === 'month') {
      const from = `${y}-${String(mo).padStart(2, '0')}-01`;
      const last = new Date(y, mo, 0).getDate();
      const to = `${y}-${String(mo).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
      return { from, to };
    }
    return { from: `${y}-01-01`, to: `${y}-12-31` };
  }

  private initMapWhenReady(): void {
    const el = this.mapHost().nativeElement;
    if (!el || this.map) {
      return;
    }
    this.map = new maplibregl.Map({
      container: el,
      style: environment.travelMapStyleUrl,
      center: [2.3522, 48.8566],
      zoom: 2,
    });
    this.map.addControl(new maplibregl.NavigationControl(), 'top-right');
    this.map.on('load', () => {
      this.mapReady = true;
      this.map!.addSource('travel-places', {
        type: 'geojson',
        data: this.buildGeoJson(),
      });
      this.map!.addLayer({
        id: 'travel-places-halo',
        type: 'circle',
        source: 'travel-places',
        filter: ['==', ['to-number', ['get', 'id']], -1],
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 2, 10, 12, 18],
          'circle-color': '#f59e0b',
          'circle-opacity': 0.35,
        },
      });
      this.map!.addLayer({
        id: 'travel-places-circle',
        type: 'circle',
        source: 'travel-places',
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 2, 4, 12, 10],
          'circle-color': [
            'match',
            ['get', 'placeStatus'],
            'VISITED',
            '#16a34a',
            'PLANNED',
            '#6366f1',
            '#94a3b8',
          ],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      });
      this.map!.on('click', 'travel-places-circle', (e: MapLayerMouseEvent) => {
        const f = e.features?.[0];
        const raw = f?.properties?.['id'];
        const id = typeof raw === 'number' ? raw : Number(raw);
        if (Number.isFinite(id)) {
          this.selectedPlaceId = id;
          const row = this.mapPlaces.find((x) => x.id === id);
          if (row && this.selectedTripId !== row.tripId) {
            this.selectTrip(row.tripId);
          }
        }
        this.setSelectionHaloFilter();
      });
      this.map!.on('mouseenter', 'travel-places-circle', () => {
        this.map!.getCanvas().style.cursor = 'pointer';
      });
      this.map!.on('mouseleave', 'travel-places-circle', () => {
        this.map!.getCanvas().style.cursor = '';
      });
      this.updateMapGeoJson();
    });
    this.resizeObserver = new ResizeObserver(() => {
      this.map?.resize();
    });
    this.resizeObserver.observe(el);
  }

  private destroyMap(): void {
    this.map?.remove();
    this.map = null;
    this.mapReady = false;
  }

  private setSelectionHaloFilter(): void {
    if (!this.map || !this.mapReady || !this.map.getLayer('travel-places-halo')) {
      return;
    }
    const sid = this.selectedPlaceId;
    if (sid == null) {
      this.map.setFilter('travel-places-halo', ['==', ['to-number', ['get', 'id']], -1]);
    } else {
      this.map.setFilter('travel-places-halo', ['==', ['to-number', ['get', 'id']], sid]);
    }
  }

  private buildGeoJson(): FeatureCollection {
    return {
      type: 'FeatureCollection',
      features: this.mapPlaces.map((p) => ({
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [p.longitude, p.latitude] },
        properties: {
          id: p.id,
          placeStatus: p.placeStatus,
          name: p.name,
          tripTitle: p.tripTitle,
        },
      })),
    };
  }

  private updateMapGeoJson(): void {
    if (!this.map || !this.mapReady) {
      return;
    }
    const src = this.map.getSource('travel-places') as GeoJSONSource | undefined;
    if (src) {
      src.setData(this.buildGeoJson());
    }
    if (this.mapPlaces.length > 0) {
      const b = new maplibregl.LngLatBounds(
        [this.mapPlaces[0].longitude, this.mapPlaces[0].latitude],
        [this.mapPlaces[0].longitude, this.mapPlaces[0].latitude],
      );
      for (const p of this.mapPlaces) {
        b.extend([p.longitude, p.latitude]);
      }
      this.map.fitBounds(b, { padding: 48, maxZoom: 12, duration: 600 });
    }
    this.setSelectionHaloFilter();
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
