package utils;

import exceptions.IncorrectCNFException;
import repositories.model.CNF.CNFModel;
import repositories.model.CNF.LiteralModel;
import repositories.model.CNF_negation.CNFNegationModel;
import repositories.model.CNF_negation.LiteralMemberNegationModel;
import repositories.model.CNF_negation.LiteralNegationModel;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import prism.PrismException;
import parser.ast.Expression;
import parser.PrismParser;
import parser.BooleanUtils;

public class ModelParser {
    public CNFNegationModel parseCNFNegationModel(File configFile) throws IncorrectCNFException, IOException {
        CNFNegationModel cnfNeg = new CNFNegationModel();
        CNFModel cnf = parseCNFModel(configFile);

        cnfNeg.setOrganizations(cnf.getOrganizations());

        for (LiteralModel literalModel : cnf.getLiterals()) {
            LiteralNegationModel literalNegationModel = new LiteralNegationModel();

            for (String str : literalModel.getLiteralMembers()) {
                LiteralMemberNegationModel memberNegationModel = new LiteralMemberNegationModel();
                if (str.startsWith("!")) {
                    memberNegationModel.setNegation(true);
                    memberNegationModel.setMemberName(str.substring(2));
                } else {
                    memberNegationModel.setNegation(false);
                    memberNegationModel.setMemberName(str);
                }

                literalNegationModel.addMember(memberNegationModel);
            }

            cnfNeg.addModel(literalNegationModel);
        }

        return cnfNeg;
    }

    public CNFModel parseCNFModel(File configFile) throws IOException, IncorrectCNFException {
        CNFModel cnf = new CNFModel();

        BufferedReader lineReader = new BufferedReader(new FileReader(configFile));
        String lineText;

        while ((lineText = lineReader.readLine()) != null) {
            if (lineText.startsWith("    Name:")) {
                cnf.addOrganization(lineText.substring(10));
            }

            if (lineText.startsWith("    Rule:")) {
                String realLiterals = lineText.substring(7, lineText.length() - 1);
                StringBuilder str = new StringBuilder();

                for (int i = 1; i < realLiterals.length(); ++i) {
                    if (realLiterals.charAt(i - 1) == '(') {
                        while (realLiterals.charAt(i) != ')') {
                            str.append(realLiterals.charAt(i));
                            ++i;
                        }
                        final Set<String> setToReturn = new LinkedHashSet<>();
                        final Set<String> tempSet = new HashSet<>();

                        for (String tempStr : str.toString().split(" AND ")) {
                            String realLiteral = tempStr;
                            if (tempStr.contains(" ")) {
                                tempStr = tempStr.substring(tempStr.lastIndexOf(" ") + 1);
                            }

                            if (tempSet.add(tempStr)) {
                                setToReturn.add(realLiteral);
                            } else {
                                throw new IncorrectCNFException("Invalid CNF formula");
                            }
                        }

                        LiteralModel literals = new LiteralModel(new ArrayList<>(tempSet));
                        literals.getLiteralMembers().sort(Comparator.comparing(item -> cnf.getOrganizations().indexOf(item)));

                        for (int j = 0; j < literals.getLiteralMembers().size(); j++) {
                            if (!setToReturn.contains(literals.getLiteralMembers().get(j))) {
                                StringBuilder tempStr = new StringBuilder(literals.getLiteralMembers().get(j));
                                literals.setLiteral(tempStr.insert(0, "! ").toString(), j);
                            }
                        }

                        cnf.addModel(literals);
                        str.setLength(0);
                    }
                }
            }
        }

        return cnf;
    }

    public Map<String, Double> parseAcceptanceProbabilities(File configFile) throws IOException, IncorrectCNFException {
        Map<String, Double> orgProbabilities = new HashMap<>();
        BufferedReader lineReader = new BufferedReader(new FileReader(configFile));
        String lineText;
        String org = null;
        double probability;

        while ((lineText = lineReader.readLine()) != null) {
            if (lineText.startsWith("    Name:")) {
                org = lineText.substring(10);
            }

            if (lineText.startsWith("    Pr:")) {
                probability = Double.parseDouble(lineText.substring(8));
                orgProbabilities.put(org, probability);
            }
        }

        StringBuilder errorMessage = new StringBuilder();

        for (Map.Entry<String, Double> orgProbs : orgProbabilities.entrySet()) {
            if (orgProbs.getValue() > 1) {
                if (!errorMessage.toString().equals("")) {
                    errorMessage.append(", ");
                }

                errorMessage.append("probability of ").append(orgProbs.getKey()).append(" is greater than one");
            } else if (orgProbs.getValue() < 0) {
                if (!errorMessage.toString().equals("")) {
                    errorMessage.append(",");
                }

                errorMessage.append("probability of ").append(orgProbs.getKey()).append(" is less than zero");
            }
        }

        if (!errorMessage.toString().equals("")) {
            throw new IncorrectCNFException(errorMessage.toString());
        }

        return orgProbabilities;
    }

    public List<int[]> getSortedSpecifications(CNFModel cnfModel) {
        List<int[]> spec = new ArrayList<>();
        List<String> orgNames = cnfModel.getOrganizations();

        for (LiteralModel literal : cnfModel.getLiterals()) {
            int[] specLiteral = new int[cnfModel.getOrganizations().size()];
            Arrays.fill(specLiteral, 1);
            List<int[]> temp = new ArrayList<>();
            temp.add(specLiteral);

            for (int i = 0; i < orgNames.size(); i++) {
                String name = orgNames.get(i);
                if (!literal.getLiteralMembers().contains(name)) {
                    List<int[]> tempClones = new ArrayList<>();
                    for (int[] t :
                            temp) {
                        int[] tClone = t.clone();
                        tClone[i] = 0;
                        tempClones.add(tClone);
                    }
                    temp.addAll(tempClones);
                }
            }

            spec.addAll(temp);
        }

        return spec;
    }


    public List<int[]> parseBackwardTransitions(File configFile, double probability, double expectedMessages) {
        return new ArrayList<>();
    }

    public List<int[]> parseSpecificationToBinary(CNFModel cnfModel) {
        return new ArrayList<>();
    }

    public String parseAndConvertToCNF(String formulaToConvert) {
        PrismParser parser = new PrismParser();
        String formulaCNFForm = "";
        try {
            Expression expr = parser.parseSingleExpression(new ByteArrayInputStream(formulaToConvert.getBytes()));
            formulaCNFForm = BooleanUtils.convertToCNF(expr.deepCopy()).toString();
        } catch (PrismException e) {
            System.out.println(String.format("Unable to convert formula *%s* to CNF format: ",
                    formulaToConvert) + e.getMessage());
        }
        return formulaCNFForm;
    }

    public static void main(String[] args) throws IncorrectCNFException, IOException {
        final String SIMPLE_SYSTEM_CONFIG_PATH = "../resources/cnf_negation.yaml";
        File file = new File(SIMPLE_SYSTEM_CONFIG_PATH);

        CNFNegationModel cnfNeg = new ModelParser().parseCNFNegationModel(file);

        for (LiteralNegationModel LNM : cnfNeg.getLiterals()) {
            System.out.println("Literal...");
            for (LiteralMemberNegationModel LMNM : LNM.getLiteralMembers()) {
                System.out.println(LMNM.isNegation() + " " + LMNM.getMemberName());
            }
        }
        
        System.out.println();
        
        for (String org : cnfNeg.getOrganizations()) {
            System.out.println(org);
        }
    }
}
