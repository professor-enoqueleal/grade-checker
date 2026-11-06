package br.com.gradechecker.google;

public class Merge {

    public static void main(String[] args) {

        int[] primary = {1, 2, 3, 0, 0, 0};
        int[] second = {2, 5, 6};

        merge(primary, 3, second, 3);

    }

    public static void merge(int[] nums1, int m, int[] nums2, int n) {

        int pointerOne = m - 1;
        int pointerTwo = n - 1;
        int endValidPosition = m + n - 1;

        while (pointerTwo >= 0) {

            if (pointerOne >= 0 && nums1[pointerOne] > nums2[pointerTwo]) {

                nums1[endValidPosition] = nums1[pointerOne];
                pointerOne--;

            } else {

                nums1[endValidPosition] = nums2[pointerTwo];
                pointerTwo--;

            }

            endValidPosition--;

        }

    }

}
