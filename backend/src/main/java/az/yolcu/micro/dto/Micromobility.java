package az.yolcu.micro.dto;

import java.util.List;

/** GET /live/micromobility (03_API_CONTRACT §5). */
public record Micromobility(List<BikeParking> bikeParkings, List<Scooter> scooters, String bikeLanesLayer) {}
