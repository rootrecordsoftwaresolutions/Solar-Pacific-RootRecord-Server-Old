package com.rootrecord.minecraft.rootmcshops;



import java.util.ArrayList;

import java.util.Collections;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.SplittableRandom;

import java.util.function.ToIntFunction;

import java.util.stream.Collectors;



/** Equal allocation across shops at the same best price tier; random when qty is 1. */

final class ShopMarketSplit {



    static final int SPLIT_DETAIL_MAX_PLAYERS = 4;



    private static final double PRICE_EPS = 0.0001;

    private static final SplittableRandom RANDOM = new SplittableRandom();



    private ShopMarketSplit() {}



    static boolean samePrice(double a, double b) {

        return Math.abs(a - b) < PRICE_EPS;

    }



    static List<ShopListing> tierAtBestPrice(List<ShopListing> sortedByBestPrice) {

        if (sortedByBestPrice.isEmpty()) {

            return List.of();

        }

        double best = sortedByBestPrice.get(0).price();

        List<ShopListing> tier = new ArrayList<>();

        for (ShopListing listing : sortedByBestPrice) {

            if (!samePrice(listing.price(), best)) {

                break;

            }

            tier.add(listing);

        }

        return List.copyOf(tier);

    }



    static Map<ShopListing, Integer> allocateEqualShare(

            List<ShopListing> shops,

            int totalQty,

            ToIntFunction<ShopListing> availableUnits) {

        LinkedHashMap<ShopListing, Integer> out = new LinkedHashMap<>();

        if (totalQty <= 0 || shops.isEmpty()) {

            return out;

        }



        List<ShopListing> eligible = shops.stream()

                .filter(shop -> availableUnits.applyAsInt(shop) > 0)

                .collect(Collectors.toCollection(ArrayList::new));

        if (eligible.isEmpty()) {

            return out;

        }



        if (totalQty == 1) {

            if (eligible.size() == 1) {

                out.put(eligible.get(0), 1);

            } else {

                out.put(eligible.get(RANDOM.nextInt(eligible.size())), 1);

            }

            return out;

        }



        allocateEqualShareRecursive(eligible, totalQty, availableUnits, out);

        return out;

    }



    private static void allocateEqualShareRecursive(

            List<ShopListing> eligible,

            int qty,

            ToIntFunction<ShopListing> availableUnits,

            Map<ShopListing, Integer> out) {

        if (qty <= 0 || eligible.isEmpty()) {

            return;

        }



        int n = eligible.size();

        int base = qty / n;

        int remainder = qty % n;



        if (base == 0) {

            for (int i = 0; i < qty; i++) {

                List<ShopListing> withCap = eligible.stream()

                        .filter(shop -> availableUnits.applyAsInt(shop) - out.getOrDefault(shop, 0) > 0)

                        .collect(Collectors.toCollection(ArrayList::new));

                if (withCap.isEmpty()) {

                    return;

                }

                ShopListing pick = withCap.get(RANDOM.nextInt(withCap.size()));

                out.merge(pick, 1, Integer::sum);

            }

            return;

        }



        List<ShopListing> order = new ArrayList<>(eligible);

        Collections.shuffle(order, RANDOM);



        int assigned = 0;

        for (int i = 0; i < order.size(); i++) {

            ShopListing shop = order.get(i);

            int target = base + (i < remainder ? 1 : 0);

            int used = out.getOrDefault(shop, 0);

            int cap = availableUnits.applyAsInt(shop) - used;

            int give = Math.min(target, cap);

            if (give > 0) {

                out.merge(shop, give, Integer::sum);

                assigned += give;

            }

        }



        int leftover = qty - assigned;

        if (leftover <= 0) {

            return;

        }



        List<ShopListing> withCap = eligible.stream()

                .filter(shop -> availableUnits.applyAsInt(shop) - out.getOrDefault(shop, 0) > 0)

                .collect(Collectors.toCollection(ArrayList::new));

        allocateEqualShareRecursive(withCap, leftover, availableUnits, out);

    }

}


