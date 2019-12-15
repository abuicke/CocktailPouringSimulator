package lt.soe.cocktailmachine;

/**
 * A cocktail glass.
 * <p>
 * A cocktail glass is defined by the volume of
 * liquid it can hold and the weight of the glass.
 * */
@SuppressWarnings("WeakerAccess")
public class CocktailGlass {

    public final int volumeMillilitres;
    public final double weightGrams;

    public CocktailGlass(int volumeMillilitres, double weightGrams) {
        this.volumeMillilitres = volumeMillilitres;
        this.weightGrams = weightGrams;
    }

}
