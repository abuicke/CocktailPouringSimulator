package lt.soe.autocock.weightsensor;

@SuppressWarnings("WeakerAccess")
public class CocktailGlass {

    public final int volumeMillilitres;
    public final double weightGrams;

    public CocktailGlass(int volumeMillilitres, double weightGrams) {
        this.volumeMillilitres = volumeMillilitres;
        this.weightGrams = weightGrams;
    }

}
