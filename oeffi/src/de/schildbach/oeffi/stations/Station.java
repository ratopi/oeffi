/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.stations;

import androidx.annotation.Nullable;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class Station {
    public final NetworkId network;
    public Location location;
    public @Nullable QueryDeparturesResult.Status departureQueryStatus = null;
    public @Nullable List<Departure> departures = null;
    private @Nullable List<LineDestination> lines = null;
    private @Nullable Product relevantProduct = null;
    public boolean hasDistanceAndBearing = false;
    public float distance;
    public float bearing;
    public @Nullable Date requestedAt = null;
    public @Nullable Date updatedAt = null;

    public Station(final NetworkId network, final Location location) {
        this.network = network;
        this.location = checkNotNull(location);
    }

    public Station(final NetworkId network, final Location location, final List<LineDestination> lines) {
        this.network = network;
        this.location = checkNotNull(location);
        setLines(lines);
    }

    public List<LineDestination> getLines() {
        return lines;
    }

    public void setLines(List<LineDestination> lines) {
        this.lines = lines;

        relevantProduct = null;
    }

    public void setDistanceAndBearing(final float distance, final float bearing) {
        this.distance = distance;
        this.bearing = bearing;
        this.hasDistanceAndBearing = true;
    }

    public Product getRelevantProduct() {
        if (relevantProduct != null)
            return relevantProduct;

        // collect all products
        final EnumSet<Product> products = EnumSet.noneOf(Product.class);
        if (location.products != null)
            products.addAll(location.products);
        final List<LineDestination> lines = this.lines;
        if (lines != null) {
            for (final LineDestination line : lines) {
                final Product product = line.line.product;
                if (product != null)
                    products.add(product);
            }
        }

        relevantProduct = !products.isEmpty() ? products.iterator().next() : null;
        return relevantProduct;
    }

    @Override
    public String toString() {
        return location.toString();
    }
}
