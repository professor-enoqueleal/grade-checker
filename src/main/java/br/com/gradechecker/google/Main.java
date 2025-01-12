package br.com.gradechecker.google;

public class Main {

    public static void main(String[] args) {

        System.out.println(isValid("[]"));
        System.out.println(isValid("()[]{}"));
        System.out.println(isValid("([)]"));
        System.out.println(isValid("()"));

    }

    public static boolean isValid(String s) {

        char[] bytes = s.toCharArray();

        for (int i = 0; i < bytes.length; i++) {

            char initConch = '[';
            char endConch = ']';

            char initParen = '(';
            char endParen = ')';

            if((bytes[i] == initParen) && (bytes[i+1] == endParen)){

                return true;

            }
            if((bytes[i] == initConch) && (bytes[i+1] == endConch)){

                return true;

            }

        }

        return false;

    }

}
