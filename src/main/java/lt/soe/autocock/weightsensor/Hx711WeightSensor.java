package lt.soe.autocock.weightsensor;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.ByteBuffer;
import java.util.Random;

public class Hx711WeightSensor {

    private static final double AVERAGE_GRAMS_PER_MILLILITRE = 0.893F;
    private static final int FLOW_RATE_MILLILITRES_PER_SECOND = 20;
    private static final double FLOW_RATE_GRAMS_PER_SECOND = AVERAGE_GRAMS_PER_MILLILITRE * (
            (double) FLOW_RATE_MILLILITRES_PER_SECOND
    );

    private static final CocktailGlass PLASTIC_COCKTAIL_GLASS = new CocktailGlass(210, 17.3);
    private static final CocktailGlass TUMBLER_COCKTAIL_GLASS = new CocktailGlass(325, 320);
    private static final CocktailGlass MARTINI_COCKTAIL_GLASS = new CocktailGlass(175, 216.67);
    private static final CocktailGlass PINA_COLADA_COCKTAIL_GLASS = new CocktailGlass(460, 303.33);
    private static final CocktailGlass SAUCER_COCKTAIL_GLASS = new CocktailGlass(200, 211);

    private static final CocktailGlass[] COCKTAIL_GLASSES = {
            PLASTIC_COCKTAIL_GLASS,
            TUMBLER_COCKTAIL_GLASS,
            MARTINI_COCKTAIL_GLASS,
            PINA_COLADA_COCKTAIL_GLASS,
            SAUCER_COCKTAIL_GLASS
    };

    private static CocktailGlass cocktailGlass;
    private static double currentSensorWeight = 0.0D;

    public static void main(String[] args) {
        System.out.println("weight sensor initialised");
        try (ZContext context = new ZContext()) {
            while (!Thread.currentThread().isInterrupted()) {
                ZMQ.Socket replySocket = context.createSocket(ZMQ.REP);
                replySocket.bind("tcp://*:5555");
                System.out.println("reply socket bound to TCP port 5555, waiting for request from server...");
                byte[] reply = replySocket.recv(0);

                boolean isFirstIngredient = reply[0] == 0;
                int ingredientQuantity = ByteBuffer.wrap(reply).getInt(1);

                if (isFirstIngredient) {
                    currentSensorWeight = 0.0D;
                }

                System.out.println("isFirstIngredient = " + isFirstIngredient);
                System.out.println("ingredientQuantity = " + ingredientQuantity + "mls");

                replySocket.send(new byte[]{0});
                replySocket.close();

                ZMQ.Socket publisherSocket = context.createSocket(ZMQ.PUB);
                publisherSocket.bind("tcp://*:5555");

                //  Ensure subscriber connection has time to complete
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    throw new IllegalStateException(ie);
                }

                try {
                    // Put the glass on the weight sensor.
                    if (isFirstIngredient) {
                        // Wait between 1 and 5 seconds to place the glass on the sensor.
                        Thread.sleep(1000 + (1000 * new Random().nextInt(5)));
                        cocktailGlass = getRandomCocktailGlass();
                        currentSensorWeight += cocktailGlass.weightGrams;
                        send(publisherSocket, currentSensorWeight);
                        System.out.println("empty glass has been placed on the " +
                                "weight sensor, weight of glass = " + currentSensorWeight + "g");
                    }
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                System.out.println("filling glass with " + ingredientQuantity + "mls of ingredient");
                double amountOfIngredientPouredWeight = 0;
                double filledIngredientWeight = ingredientQuantity * AVERAGE_GRAMS_PER_MILLILITRE;
                while (amountOfIngredientPouredWeight < filledIngredientWeight) {
                    // Get flow rate per tenth of a second.
                    amountOfIngredientPouredWeight += FLOW_RATE_GRAMS_PER_SECOND * 0.1;
                    System.out.println("poured " +
                            (amountOfIngredientPouredWeight / AVERAGE_GRAMS_PER_MILLILITRE) +
                            "mls of " + ingredientQuantity + "mls");
                    send(publisherSocket, currentSensorWeight + amountOfIngredientPouredWeight);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }

                currentSensorWeight += filledIngredientWeight;
                send(publisherSocket, -1);
                System.out.println("glass has been filled");
                publisherSocket.close();
            }
        }
    }

    private static CocktailGlass getRandomCocktailGlass() {
        int randomIndex = new Random().nextInt(COCKTAIL_GLASSES.length);
        return COCKTAIL_GLASSES[randomIndex];
    }

    private static void send(ZMQ.Socket zeroMqSocket, double updatedSensorWeight) {
        byte[] bytes = ByteBuffer.allocate(8).putDouble(updatedSensorWeight).array();
        zeroMqSocket.send("weight sensor topic", ZMQ.SNDMORE);
        zeroMqSocket.send(bytes);
    }

}
